window.CommonUtil = {
  chooseCategory: function (defaultSelected, callBack) {
      var randFunName = "fun" + new Date().getTime();
      window[randFunName] = callBack;
      layui.use('layer', function () {
          var layer = layui.layer;
          layer.open({
              type: 2,
              title: ["选择分类", 'font-size:18px;'],
              area: ['500px', '600px'],
              fixed: true,
              resize: false,
              offset: '100px',
              content: '/board/tree?defaultSelected=' + defaultSelected + '&callback=' + randFunName
          });
      });
  }
}