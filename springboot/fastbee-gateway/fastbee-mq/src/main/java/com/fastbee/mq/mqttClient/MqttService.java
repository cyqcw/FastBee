package com.fastbee.mq.mqttClient;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fastbee.common.core.mq.DeviceReportBo;
import com.fastbee.common.enums.DeviceStatus;
import com.fastbee.common.enums.ServerType;
import com.fastbee.common.utils.DateUtils;
import com.fastbee.common.utils.gateway.mq.TopicsPost;
import com.fastbee.common.utils.gateway.mq.TopicsUtils;
import com.fastbee.iot.domain.Device;
import com.fastbee.iot.service.IDeviceService;
import com.fastbee.mq.service.IDeviceReportMessageService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


@Component
@Slf4j
public class MqttService {

    @Resource
    private TopicsUtils topicsUtils;
    @Resource
    private IDeviceReportMessageService deviceReportMessageService;

    @Resource
    private IDeviceService deviceService;

    private final WebClient webClient;
    private static final String DINGTALK_WEBHOOK = "https://oapi.dingtalk.com/robot/send?access_token=b5bfecb8c553f2fbde9a566c46d66e30625c848decf15cc17886d011f05d8a65";

    public MqttService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(DINGTALK_WEBHOOK).build();
    }

    public void subscribe(MqttAsyncClient client) throws MqttException {
        TopicsPost allPost = topicsUtils.getAllPost();
        client.subscribe(allPost.getTopics(), allPost.getQos());
        log.info("mqtt监控主题,{}", Arrays.asList(allPost.getTopics()));
    }

    /**
     * 消息回调方法
     *
     * @param topic       主题
     * @param mqttMessage 消息体
     */
    public void subscribeCallback(String topic, MqttMessage mqttMessage) {

        String message = new String(mqttMessage.getPayload());
        log.info("接收消息主题 : " + topic);
        log.info("接收消息Qos : " + mqttMessage.getQos());
        log.info("接收消息内容 : " + message);
        String serialNumber = topicsUtils.parseSerialNumber(topic);
        Long productId = topicsUtils.parseProductId(topic);
        String name = topicsUtils.parseTopicName(topic);

        DeviceReportBo reportBo = DeviceReportBo.builder()
                .serialNumber(serialNumber)
                .productId(productId)
                .data(mqttMessage.getPayload())
                .platformDate(DateUtils.getNowDate())
                .topicName(topic)
                .serverType(ServerType.MQTT)
                .build();
        if (name.startsWith("property")) {
            deviceReportMessageService.parseReportMsg(reportBo);
        }
        /**
         * 发布设备信息
         * {
         *      "rssi": -43,
         *      "firmwareVersion": 1.2,
         *      "status": 3,
         *      "userId": "1",
         *      "summary": {}
         * }
         */
        else if (name.startsWith("info") && topicsUtils.parseTopicName4(topic).equals("post")) {
            Device device = deviceService.selectDeviceBySerialNumber(serialNumber);
            if (device == null) {
                log.error("设备不存在,{}", serialNumber);
                return;
            }
            Device newDeviceData = JSON.parseObject(message, Device.class);
            Field[] fields = Device.class.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object newValue = field.get(newDeviceData);
                    if (newValue != null) {
                        if (field.getName().equals("status") && field.get(device)==Integer.valueOf(DeviceStatus.UNACTIVATED.getType())){
                            device.setActiveTime(DateUtils.getNowDate());
                        }
                        field.set(device, newValue);
                    }
                } catch (IllegalAccessException e) {
                    log.error("无法更新字段: {}", field.getName(), e);
                }
            }
            deviceService.updateDevice(device);
        }
        /**
         * 发布事件（记录预警情况）
         */
        else if (name.startsWith("event") && topicsUtils.parseTopicName4(topic).equals("post")) {
            String deviceName = deviceService.selectDeviceBySerialNumber(serialNumber).getDeviceName();
            sendAlarm(deviceName, message);
            log.info("设备{} 发生事件,描述{}", serialNumber, message);
        }
    }

    /**
     * 发送报警信息
     *
     * @param device 设备
     * @param message 报警信息
     */
    private void sendAlarm(String device, String message) {
            //钉钉的webhook
            //请求的JSON数据，这里用map在工具类里转成json格式
            Map<String,Object> json = new HashMap();
            Map<String,Object> text=new HashMap();
            json.put("msgtype","text");
            text.put("content", String.format("Alarm!\n%s : \n%s", device, message));
            json.put("text",text);

            //发送post请求
            String response = SendHttps.sendPostByMap(DINGTALK_WEBHOOK, json);
            log.info("项目告警发送钉钉，响应结果：{}", response);
    }
}
