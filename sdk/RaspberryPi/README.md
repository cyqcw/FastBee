## 硬件端树莓派SDK说明

##### 运行环境
- Python 3.7.2 (其他python3的版本一般也可以)


- 开发板：树莓派4b（没有加入硬件相关代码，安装好python3环境，win下，linux下都能运行）


- 库 需要安装库


1. mqtt库
   
   ```
   pip install paho-mqtt
   ```
   
2. ase加密库

   ```
   pip uninstall crypto
   
   pip uninstall pycryptodome 
   
   pip install pycryptodome
   ```

   前面两个卸载命令是为了防止一些安装环境问题

3. 报错缺少xx库，命令

   ```
   pip install xx
   ```

##### 运行程序

```
python3 main_sdk.py
```

程序运行依赖aes.py文件，保证该文件和main_sdk.py在同一目录


##### 开发参考资料：

[Eclipse Paho MQTT Python Client 使用手册 | Cooooder](https://www.cooooder.com/archives/20210303)

[Python 实现AES加密 - 知乎 (zhihu.com)](https://zhuanlan.zhihu.com/p/261694311)

[(5条消息) python实现AES加密解密_Hello_wshuo的博客-CSDN博客_python实现aes加密](https://blog.csdn.net/chouzhou9701/article/details/122019967)

[使用python time()方法-Python学习网](http://www.py.cn/jishu/jichu/20424.html)

[python 线程定时器Timer（37） - 知乎 (zhihu.com)](https://zhuanlan.zhihu.com/p/91412537)

[浅谈Python的格式化输出_python_脚本之家 (jb51.net)](https://www.jb51.net/article/225609.htm)

[Python Request库入门 - 简书 (jianshu.com)](https://www.jianshu.com/p/d78982126318)

[Python JSON | 菜鸟教程 (runoob.com)](https://www.runoob.com/python/python-json.html)

[Python random() 函数 | 菜鸟教程 (runoob.com)](https://www.runoob.com/python/func-number-random.html)


