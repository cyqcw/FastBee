<template>
  <div style="padding-left: 20px">
    <el-form :inline="true" label-width="100px">
<!--      <el-form-item label="监测间隔(ms)">-->
<!--        <el-tooltip class="item" effect="light" content="取值范围500-10000毫秒" placement="top">-->
<!--          <el-input v-model="monitorInterval" placeholder="请输入监测间隔" type="number" clearable size="small" style="width: 180px" />-->
<!--        </el-tooltip>-->
<!--      </el-form-item>-->
      <el-form-item label="监测次数">
        <el-tooltip class="item" effect="light" content="取值方位1-300" placement="top">
          <el-input v-model="monitorNumber" placeholder="请输入监测次数" type="number" clearable size="small" style="width: 180px" />
        </el-tooltip>
      </el-form-item>
      <el-form-item>
        <el-button type="success" icon="el-icon-video-play" size="mini" @click="beginMonitor()" style="margin-left: 30px">开始监测</el-button>
        <el-button type="danger" icon="el-icon-video-pause" size="mini" @click="stopMonitor()">停止监测</el-button>
      </el-form-item>
    </el-form>
    <el-row :gutter="20" v-loading="chartLoading" element-loading-text="正在接收设备数据，请耐心等待......" element-loading-spinner="el-icon-loading" element-loading-background="rgba(0, 0, 0, 0.8)">
      <el-col :span="12" v-for="(item, index) in monitorThings" :key="index" style="margin-bottom: 20px">
        <el-card shadow="hover" :body-style="{ paddingTop: '10px', marginBottom: '-20px' }">
          <div ref="monitor" style="height: 210px; padding: 0"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="120">
      <el-col :xs="100" :sm="100" :md="100" :lg="40" :xl="60">
        <!-- 设备监测图表-->
        <template v-if="deviceInfo.chartList.length > 0">
          <el-row :gutter="20" style="background-color: #f5f7fa; padding: 20px 10px 20px 10px; border-radius: 15px; margin-right: 5px">
            <!-- 修改这里，确保四个图表能在同一行显示 -->
            <el-col :xs="6" :sm="6" :md="6" :lg="6" :xl="6" v-for="(item, index) in monitorThings" :key="index">
              <el-card shadow="hover" style="border-radius: 30px; margin-bottom: 20px">
                <div ref="map" style="height: 230px; width: 185px; margin: 0 auto"></div>
              </el-card>
            </el-col>
          </el-row>
          <!-- 如果有更多图表，考虑分页或滚动显示 -->
        </template>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import Template from '../template/index.vue';

export default {
  name: 'DeviceMonitor',
  components: { Template },
  props: {
    device: {
      type: Object,
      default: null,
    },
  },
  watch: {
    // 获取到父组件传递的device后，刷新列表
    device: function (newVal, oldVal) {
      this.deviceInfo = newVal;
      if (this.deviceInfo && this.deviceInfo.deviceId != 0) {
        // 监测数据
        this.monitorThings = this.deviceInfo.monitorList;
        console.log('监测数据：', this.monitorThings);
        console.log('设备信息：', this.deviceInfo);
        // 监测数据集合初始化
        this.dataList = [];
        for (let i = 0; i < this.monitorThings.length; i++) {
          this.dataList.push( {
            id: this.monitorThings[i].id,
            name: this.monitorThings[i].name,
            data: [],
          });
          // this.dataList[i].data.push(["2022-03-14 23:32:09", "30"]);
        }
        // 绘制监测图表
        this.$nextTick(function () {
          this.getMonitorChart();
        });
        this.mqttCallback();
      }
    },
  },
  data() {
    return {
      // 实时监测间隔
      monitorInterval: 1000,
      // 实时监测次数
      monitorNumber: 60,
      // 图表集合
      chart: [],
      // 图表数据集合
      dataList: [],
      // 监测物模型
      monitorThings: [],
      // 图表遮罩层
      chartLoading: false,
      // 设备信息
      deviceInfo: {},
      // 监测图表
      monitorChart: [
        {
          chart: {},
          data: {
            id: '',
            name: '',
            value: '',
          },
        },
      ],
    };
  },
  created() {},
  methods: {
    /**
     * Mqtt发布消息
     * @device 设备
     * @model 物模型 ,type 类型(1=属性，2=功能，3=OTA升级(商业版支持)，4=实时监测)
     * */
    mqttPublish(device, model) {
      let topic = '';
      let message = '';
      if (model.type == 4) {
        // 实时监测
        topic = '/' + device.productId + '/' + device.serialNumber + '/monitor/get';
        message = '{"count":' + model.value + ',"interval":' + this.monitorInterval + '}';
      } else {
        return;
      }
      if (topic != '') {
        // 发布
        this.$mqttTool
          .publish(topic, message, model.name)
          .then((res) => {
            this.$modal.notifySuccess(res);
          })
          .catch((res) => {
            this.$modal.notifyError(res);
          });
      }
    },
    /* Mqtt回调处理  */
    mqttCallback() {
      this.$mqttTool.client.on('message', (topic, message, buffer) => {
        let topics = topic.split('/');
        let productId = topics[1];
        let deviceNum = topics[2];
        message = JSON.parse(message.toString());
        if (!message) {
          return;
        }
        if (topics[3] == 'status') {
          console.log('接收到【设备状态】主题：', topic);
          console.log('接收到【设备状态】内容：', message);
          // 更新列表中设备的状态
          if (this.deviceInfo.serialNumber == deviceNum) {
            this.deviceInfo.status = message.status;
            this.deviceInfo.isShadow = message.isShadow;
            this.deviceInfo.rssi = message.rssi;
          }
        }
        if (topics[3] == 'monitor') {
          console.log('接收到【实时监测】主题：', topic);
          console.log('接收到【实时监测】内容：', message);
          // 实时监测
          this.chartLoading = false;
          // 根据最新的message更新下方表盘数据
          this.MonitorChart(message);

          for (let k = 0; k < message.length; k++) {
            let value = message[k].value;
            let id = message[k].id;
            let remark = message[k].remark;
            // 数据加载到图表
            for (let i = 0; i < this.dataList.length; i++) {
              if (id == this.dataList[i].id) {
                // 普通类型匹配
                if (this.dataList[i].length > 50) {
                  this.dataList[i].shift();
                }
                this.dataList[i].data.push([this.getTime(), value]);
                // 更新图表
                this.chart[i].setOption({
                  series: [
                    {
                      data: this.dataList[i].data,
                    },
                  ],
                });
                break;
              } else if (this.dataList[i].id.indexOf('array_') == 0) {
                // 数组类型匹配,例如：gateway_temperature,图表id去除前缀后匹配
                let index = this.dataList[i].id.substring(6, 8);
                let identity = this.dataList[i].id.substring(9);
                if (identity == id) {
                  let values = value.split(',');
                  if (this.dataList[i].length > 50) {
                    this.dataList[i].shift();
                  }
                  this.dataList[i].data.push([this.getTime(), values[index]]);
                  // 更新图表
                  this.chart[i].setOption({
                    series: [
                      {
                        data: this.dataList[i].data,
                      },
                    ],
                  });
                  break;
                }
              }
            }
          }
        }
      });
    },
    /** 更新实时监测参数*/
    beginMonitor() {
      if (this.deviceInfo.status != 3) {
        this.$modal.alertError('设备不在线，下发指令失败');
        return;
      }
      // 清空图表数据
      for (let i = 0; i < this.dataList.length; i++) {
        this.dataList[i].data = [];
      }
      if (this.monitorInterval < 500 || this.monitorInterval > 10000) {
        this.$modal.alertError('实时监测的间隔范围500-10000毫秒');
      }
      if (this.monitorNumber == 0 || this.monitorNumber > 300) {
        this.$modal.alertError('实时监测数量范围1-300');
      }
      // Mqtt发布实时监测消息
      let model = {};
      model.name = '更新实时监测';
      model.value = this.monitorNumber;
      model.type = 4;
      this.mqttPublish(this.deviceInfo, model);
      this.chartLoading = true;
    },
    /** 停止实时监测 */
    stopMonitor() {
      if (this.deviceInfo.status != 3) {
        this.$modal.alertError('设备不在线，下发指令失败');
        return;
      }
      this.chartLoading = false;
      // Mqtt发布实时监测
      let model = {};
      model.name = '关闭实时监测';
      model.value = 0;
      model.type = 4;
      this.mqttPublish(this.deviceInfo, model);
    },
    /**监测数据 */
    getMonitorChart() {
      let color = ['#1890FF', '#91CB74', '#FAC858', '#EE6666', '#73C0DE', '#3CA272', '#FC8452', '#9A60B4', '#ea7ccc'];
      for (let i = 0; i < this.monitorThings.length; i++) {
        // 设置宽度
        this.$refs.monitor[i].style.width = document.documentElement.clientWidth / 2 - 255 + 'px';
        this.chart[i] = this.$echarts.init(this.$refs.monitor[i]);
        var option;
        option = {
          title: {
            left: 'center',
            text: this.monitorThings[i].name + ' （单位 ' + (this.monitorThings[i].datatype.unit != undefined ? this.monitorThings[i].datatype.unit : '无') + '）',
            textStyle: {
              fontSize: 14,
            },
          },
          grid: {
            top: '50px',
            left: '20px',
            right: '20px',
            bottom: '10px',
            containLabel: true,
          },
          tooltip: {
            trigger: 'axis',
            axisPointer: {
              animation: true,
            },
          },
          xAxis: {
            type: 'time',
            show: false,
            splitLine: {
              show: false,
            },
          },
          yAxis: {
            type: 'value',
            boundaryGap: [0, '100%'],
            splitLine: {
              show: true,
            },
          },
          series: [
            {
              name: this.monitorThings[i].name,
              type: 'line',
              symbol: 'none',
              sampling: 'lttb',
              itemStyle: {
                color: i > 9 ? color[0] : color[i],
              },
              areaStyle: {},
              data: [],
            },
          ],
        };
        option && this.chart[i].setOption(option);
      }
    },
    /* 获取当前时间*/
    getTime() {
      let date = new Date();
      let y = date.getFullYear();
      let m = date.getMonth() + 1;
      let d = date.getDate();
      let H = date.getHours();
      let mm = date.getMinutes();
      let s = date.getSeconds();
      m = m < 10 ? '0' + m : m;
      d = d < 10 ? '0' + d : d;
      H = H < 10 ? '0' + H : H;
      return y + '-' + m + '-' + d + ' ' + H + ':' + mm + ':' + s;
    },

    /* 仪表盘展示 */
    MonitorChart(message) {
      console.log('仪表盘展示：', message);
      for (let i = 0; i < this.monitorThings.length; i++) {
        this.monitorChart[i] = {
          chart: this.$echarts.init(this.$refs.map[i]),
          data: {
            id: this.monitorThings[i].id,
            name: this.monitorThings[i].name,
            value: this.monitorThings.shadow ? this.monitorThings[i].shadow : this.deviceInfo.chartList[i].datatype.min,
          },
        };
        console.log('bnuu：', this.monitorChart[i]);


        var option;
        option = {
          tooltip: {
            formatter: ' {b} <br/> {c}' + this.deviceInfo.chartList[i].datatype.unit,
          },
          series: [
            {
              name: this.deviceInfo.chartList[i].datatype.type,
              type: 'gauge',
              min: this.deviceInfo.chartList[i].datatype.min,
              max: this.deviceInfo.chartList[i].datatype.max,
              colorBy: 'data',
              splitNumber: 10,
              radius: '100%',
              // 分割线
              splitLine: {
                distance: 4,
              },
              axisLabel: {
                fontSize: 10,
                distance: 10,
              },
              // 刻度线
              axisTick: {
                distance: 4,
              },
              // 仪表盘轴线
              axisLine: {
                lineStyle: {
                  width: 8,
                  color: [
                    [0.2, '#409EFF'], // 0~20%
                    [0.8, '#12d09f'], // 40~60%
                    [1, '#F56C6C'], // 80~100%
                  ],
                  opacity: 0.3,
                },
              },
              pointer: {
                icon: 'triangle',
                length: '60%',
                width: 7,
              },
              progress: {
                show: true,
                width: 8,
              },
              detail: {
                valueAnimation: true,
                formatter: '{value}' + ' ' + this.deviceInfo.chartList[i].datatype.unit,
                offsetCenter: [0, '80%'],
                fontSize: 20,
              },
              data: [
                {
                  value: message[i].value,
                  name: this.deviceInfo.chartList[i].name,
                },
              ],
              title: {
                offsetCenter: [0, '115%'],
                fontSize: 16,
              },
            },
          ],
        };
        option && this.monitorChart[i].chart.setOption(option);
      }
    },
  },
};
</script>
