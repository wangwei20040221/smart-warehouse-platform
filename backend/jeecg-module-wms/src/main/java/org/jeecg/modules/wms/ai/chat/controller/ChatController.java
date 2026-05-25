package org.jeecg.modules.wms.ai.chat.controller;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.util.JwtUtil;
import org.jeecg.common.util.*;
import org.jeecg.modules.airag.app.entity.AiragApp;
import org.jeecg.modules.airag.app.service.IAiragAppService;
import org.jeecg.modules.airag.app.vo.ChatSendParams;
import org.jeecg.modules.wms.ai.chat.tools.WmsInventoryTools;
import org.jeecg.modules.wms.ai.chat.tools.WmsShipmentTools;
import org.jeecg.modules.wms.ai.chat.util.VectorDistanceUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

/**
 * @author Mr.M
 * @version 1.0
 * @description 星辰wms的AI接口
 * @date 2025/12/6 10:46
 */
@RestController
@Slf4j
@RequestMapping("/ai")
public class ChatController {

    //    @Resource(name = "chatClientOllama")
    @Resource(name = "chatClientOpenAi")
    private ChatClient chatClient;

    @Autowired
    private IAiragAppService airagAppService;

    @Autowired
    private WmsInventoryTools wmsInventoryTools;

    @Autowired
    private WmsShipmentTools wmsShipmentTools;

    /**
     * 启动时验证 Tool 检测是否正常，并缓存 ToolCallback 数组
     */
    private ToolCallback[] cachedToolCallbacks;

    @PostConstruct
    void initTools() {
        ToolCallback[] inventoryCallbacks = ToolCallbacks.from(wmsInventoryTools);
        ToolCallback[] shipmentCallbacks = ToolCallbacks.from(wmsShipmentTools);
        cachedToolCallbacks = new ToolCallback[inventoryCallbacks.length + shipmentCallbacks.length];
        System.arraycopy(inventoryCallbacks, 0, cachedToolCallbacks, 0, inventoryCallbacks.length);
        System.arraycopy(shipmentCallbacks, 0, cachedToolCallbacks, inventoryCallbacks.length, shipmentCallbacks.length);
        log.info("========== Tool 检测结果 ==========");
        log.info("wmsInventoryTools class: {}", wmsInventoryTools.getClass().getName());
        log.info("wmsShipmentTools class: {}", wmsShipmentTools.getClass().getName());
        log.info("检测到 {} 个 ToolCallback:", cachedToolCallbacks.length);
        for (ToolCallback tc : cachedToolCallbacks) {
            log.info("  - {} : {}", tc.getName(), tc.getDescription());
        }
        log.info("===================================");
    }

    // 请求方式和路径不要改动，将来要与前端联调
//    @RequestMapping("/chat")
//    public String chat(String prompt) {
//        //调用大模型
//        String content = chatClient
//                .prompt(prompt)
//                .call()//同步调用方法，等大模型全部返回才结束
//                .content();
//        return content;
//    }

    @RequestMapping(value = "/chat", produces = "text/html;charset=UTF-8")//加produces为了防止乱码
    public Flux<String> chat(String prompt) {
        //调用大模型
        Flux<String> flux = chatClient
                .prompt(prompt)
                .stream()//流式调用
                .content();

        return flux;
    }

    /**
     * 获取questionAnswerAdvisor 对象
     * @param knowIds 知识库ids
     * @return
     */
    private QuestionAnswerAdvisor getQuestionAnswerAdvisor(List<String>  knowIds){
        StringBuilder stringBuilder = new StringBuilder();
        //将knowIds拼接knowledgeId == '1958811713520881665' || knowledgeId == '222222'
        Iterator<String> iterator = knowIds.iterator();
        while (iterator.hasNext()) {
            String knowId = iterator.next();
            stringBuilder.append("knowledgeId == '").append(knowId).append("'");
            if (iterator.hasNext()) {
                stringBuilder.append(" || ");
            }
        }

        QuestionAnswerAdvisor questionAnswerAdvisor = new QuestionAnswerAdvisor(
                pgVectorStore, // 用来访问向量数据库的
                SearchRequest.builder() // 向量检索的请求参数
                        .similarityThreshold(0.5d) // 相似度阈值
                        .filterExpression(stringBuilder.toString())
                        .topK(2) // 返回的文档片段数量
                        .build());
        return questionAnswerAdvisor;
    }

    @RequestMapping(value = "/chat/send")
    public SseEmitter send(@RequestBody ChatSendParams chatSendParams, HttpServletRequest httpRequest) {

        AssertUtils.assertNotEmpty("参数异常", chatSendParams);
        //用户提示词
        String userMessage = chatSendParams.getContent();
        AssertUtils.assertNotEmpty("至少发送一条消息", userMessage);

        // 获取会话信息
        String conversationId = chatSendParams.getConversationId();//会话id
        String topicId = oConvertUtils.getString(chatSendParams.getTopicId(), UUIDGenerator.generate());
        //获取当前用户的账号
        String username=getUsername(httpRequest);
        chatSendParams.setUsername( username);
        // 每次会话都生成一个新的,用来缓存emitter
        String requestId = UUIDGenerator.generate();
        SseEmitter emitter = new SseEmitter(600000L);
        emitter.onCompletion(() -> log.info("SSE completed: requestId={}", requestId));
        emitter.onTimeout(() -> log.error("SSE timeout: requestId={}", requestId));
        emitter.onError(e -> log.error("SSE error: requestId={}", requestId, e));
        // 获取app信息
        AiragApp app = null;
        List<String> knowIds = null;
        if (oConvertUtils.isNotEmpty(chatSendParams.getAppId())) {
            app = airagAppService.getById(chatSendParams.getAppId());
            //获取知识库id
            knowIds = app.getKnowIds();
        }

        //将chatSendParams对象转成json
        String json = JSONObject.toJSONString(chatSendParams);
        Flux<String> flux = null;
        if(app == null){
           flux = chatClient
                    .prompt(userMessage)
                    .system("你是星辰WMS仓储管理系统的AI助手。你可以帮助用户完成以下操作：\n" +
                            "1. 查询库存信息（按商品、仓库、货主、储位等条件）\n" +
                            "2. 查询所有仓库列表\n" +
                            "3. 查询所有商品列表\n" +
                            "4. 查询所有货主信息\n" +
                            "5. 查询商品发货快递单号和物流公司信息\n" +
                            "如果用户询问你能做什么，请用中文简要介绍以上功能。")
                    .advisors(a->a.param(CHAT_MEMORY_CONVERSATION_ID_KEY,json))
                    .tools(cachedToolCallbacks)
                    .stream()//流式调用，支持 Tool Calling
                    .content();
        }else{
            //如果knowIds不为空
            if(knowIds != null && knowIds.size() > 0){
                QuestionAnswerAdvisor questionAnswerAdvisor = getQuestionAnswerAdvisor(knowIds);
                flux = chatClient
                        .prompt(userMessage)
                        .user(userMessage)
                        .system(app.getPrompt())//添加系统提示词
                        .advisors(a->a.param(CHAT_MEMORY_CONVERSATION_ID_KEY,json))
                        .advisors(questionAnswerAdvisor)
                        .tools(cachedToolCallbacks)
                        .stream()//流式调用
                        .content();
            }else{
                flux = chatClient
                        .prompt(userMessage)
                        .user(userMessage)
                        .system(app.getPrompt())//添加系统提示词
                        .advisors(a->a.param(CHAT_MEMORY_CONVERSATION_ID_KEY,json))
                        .tools(cachedToolCallbacks)
                        .stream()//流式调用
                        .content();
            }

        }

        /**
         * 是否正在思考
         */
        AtomicBoolean isThinking = new AtomicBoolean(false);

        //向前端发送两条消息
        try {
            sendMessage(emitter, conversationId, topicId, requestId, ">", "MESSAGE");
            sendMessage(emitter, conversationId, topicId, requestId, "\n> ", "MESSAGE");
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        log.info("About to subscribe to flux for requestId={}", requestId);
        flux.subscribe(
                data -> {
                    log.debug("Flux data received for requestId={}: {}", requestId, data);
                    // 兼容推理模型
                    if ("<think>".equals(data)) {
                        isThinking.set(true);
                        data = "> ";
                    }
                    if ("</think>".equals(data)) {
                        isThinking.set(false);
                        data = "\n\n";
                    }
                    if (isThinking.get()) {
                        if (null != data && data.contains("\n")) {
                            data = "\n> ";
                        }
                    }
                    // 发送消息
                    try {
                        sendMessage(emitter, conversationId, topicId, requestId, data, "MESSAGE");
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    // 错误处理
                    log.error("AI Chat SSE stream error for requestId={}", requestId, error);
                    emitter.completeWithError(error);
                },
                () -> {
                    // 完成处理
                    log.info("Flux completed for requestId={}", requestId);
                    try {
                        sendEndMessage(emitter, conversationId, topicId, requestId);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                }
        );
        log.info("Flux subscribed for requestId={}", requestId);
        return emitter;
    }

    // 辅助方法：向客户端发送消息
    private void sendMessage(SseEmitter emitter, String conversationId, String topicId,
                             String requestId, String message,String event) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversationId);
        response.put("topicId", topicId);
        response.put("requestId", requestId);
        response.put("event", event);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("message", message);
        response.put("data", messageData);

        String jsonData =  JSONObject.toJSONString(response);
        emitter.send(SseEmitter.event().data(jsonData));
    }
    // 辅助方法：发送结束消息
    private void sendEndMessage(SseEmitter emitter, String conversationId, String topicId,
                                String requestId) throws IOException {
        Map<String, Object> endResponse = new HashMap<>();
        endResponse.put("event", "MESSAGE_END");
        endResponse.put("flowId", null);
        endResponse.put("requestId", requestId);
        endResponse.put("conversationId", conversationId);
        endResponse.put("topicId", topicId);
        endResponse.put("data", null);

        String endJson =  JSONObject.toJSONString(endResponse);
        emitter.send(SseEmitter.event().data(endJson));
    }

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ISysBaseAPI sysBaseApi;

    private String getUsername(HttpServletRequest httpRequest) {
        try {
            TokenUtils.getTokenByRequest();
            String token;
            if(null != httpRequest){
                token = TokenUtils.getTokenByRequest(httpRequest);
            }else{
                token = TokenUtils.getTokenByRequest();
            }
            if (TokenUtils.verifyToken(token, sysBaseApi, redisUtil)) {
                return JwtUtil.getUsername(token);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    @Resource
    private OpenAiEmbeddingModel openAiEmbeddingModel;

    @Resource(name = "pgVectorStore")
    private VectorStore pgVectorStore;


    @GetMapping(value = "/embedtest")
    public String embedtest(String query,String t1,String t2) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("query: "+ query);
        sb.append("</br>");
        // 先将查询文本向量化,
        float[] queryVector = openAiEmbeddingModel.embed(query);
        sb.append("query向量: "+ arrayToString(queryVector));
        sb.append("</br>");
        sb.append("==================================");
        sb.append("</br>");
        sb.append("t1: "+ t1);
        sb.append("</br>");
        float[] embed1 = openAiEmbeddingModel.embed(t1);
        sb.append("t1向量: "+arrayToString(embed1));
        sb.append("</br>");
        sb.append("相似度,比较比较欧氏距离: "+  VectorDistanceUtils.euclideanDistance(queryVector, embed1));
        sb.append("</br>");
        sb.append("相似度,比较比较余弦距离: "+  VectorDistanceUtils.cosineDistance(queryVector, embed1));
        sb.append("</br>");
        sb.append("==================================");
        sb.append("</br>");
        sb.append("t2: "+ t2);
        sb.append("</br>");
        float[] embed2 = openAiEmbeddingModel.embed(t2);
        sb.append("t2向量: "+ arrayToString(embed2));
        sb.append("</br>");
        sb.append("相似度,比较比较欧氏距离: "+  VectorDistanceUtils.euclideanDistance(queryVector, embed2));
        sb.append("</br>");
        sb.append("相似度,比较比较余弦距离: "+  VectorDistanceUtils.cosineDistance(queryVector, embed2));

        //向向量数据库写两个文档
        Document document = new Document(t1);
        Document document2 = new Document(t2);
        //将两个文档加入list
        List<Document> documents = new ArrayList<>();
        documents.add(document);
        documents.add(document2);
        //借助向量模型生成向量，将向量添加到向量数据库中
        pgVectorStore.add(documents);

        return sb.toString();
    }
    // 辅助方法：将float数组格式化为字符串
    private String arrayToString(float[] array) {
        if (array == null) return "null";
        if (array.length == 0) return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        // 限制显示长度，避免输出过长的向量
        int limit = Math.min(array.length, 10); // 只显示前10个元素
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%.6f", array[i]));
            if (i < limit - 1) {
                sb.append(", ");
            }
        }

        if (array.length > limit) {
            sb.append(", ... (").append(array.length).append(" dimensions)");
        }

        sb.append("]");
        return sb.toString();
    }
}
