package com.fastbee.mq.mqttClient;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fastbee.common.constant.FastBeeConstant;
import com.fastbee.common.core.mq.DeviceReportBo;
import com.fastbee.common.enums.DeviceStatus;
import com.fastbee.common.enums.ServerType;
import com.fastbee.common.utils.DateUtils;
import com.fastbee.common.utils.gateway.mq.TopicsPost;
import com.fastbee.common.utils.gateway.mq.TopicsUtils;
import com.fastbee.iot.domain.Device;
import com.fastbee.iot.service.IDeviceService;
import com.fastbee.mq.redischannel.producer.MessageProducer;
import com.fastbee.mq.service.IDeviceReportMessageService;
import com.fastbee.mq.service.IMessagePublishService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Arrays;


@Component
@Slf4j
public class MqttService {

    @Resource
    private TopicsUtils topicsUtils;
    @Resource
    private IDeviceReportMessageService deviceReportMessageService;

    @Resource
    private IDeviceService deviceService;


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
         *      "longitude": 0,
         *      "latitude": 0,
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
            log.info("设备{} 发生事件,描述{}", serialNumber, message);
        }
    }
}
