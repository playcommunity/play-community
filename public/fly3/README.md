Fly Template 由 layui 官方社区（Fly社区）倾情抽取，页面基于 layui 搭建而成，可很好地作为简约通用型社区的页面支撑。我们希望通过该模板表明我们对 layui 执着的信念、以及对未来持续加强的承诺。

# 目录说明  
```
├─html 可直接预览的模板文件
├─res 静态资源
│  ├─css
│  ├─images
│  ├─layui
│  └─mods 模板业务模块
└─views 动态模板参考（NodeJS端）
```

# 字符解析
该模板自带一个特定语法的编辑器，当你把内容存储到数据库后，在页面读取后浏览，会发现诸如“表情、代码、图片”等无法解析，这是因为需要对该内容进行一次转义，通常来说这是在服务端完成的，但鉴于简单化，你还可以直接在前端去解析，在模板的detail.html中，我们已经把相关的代码写好了，你只需打开注释即可（在代码的最下面）。

当然，如果觉得编辑器无法满足你的需求，你也可以把该编辑器换成别的HTML编辑器或MarkDown编辑器。

# 预览地址
http://www.layui.com/template/fly/demo/html/catalog.html

# 开源协议
MIT License

# 社区相关
* [Fly社区](http://fly.layui.com/)
* [码云](https://gitee.com/sentsin/fly/)
* [GitHub](https://github.com/layui/fly)
