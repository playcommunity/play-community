layui.define(['laypage', 'fly'], function(exports){

  var $ = layui.jquery;
  var layer = layui.layer;
  var util = layui.util;
  var laytpl = layui.laytpl;
  var form = layui.form;
  var laypage = layui.laypage;
  var upload = layui.upload;
  var fly = layui.fly;
  var device = layui.device();
  

  //求解管理
  var active = {
    //提交案例
    pushLeader: function(div){
      layer.open({
        type: 1
        ,id: 'LAY_pushcase'
        ,title: '提交博客'
        ,area: (device.ios || device.android) ? ($(window).width() + 'px') : '660px'
        ,content: ['<ul class="layui-form" style="margin: 20px;">'
          ,'<li class="layui-form-item">'
            ,'<label class="layui-form-label">博主名称</label>'
            ,'<div class="layui-input-block">'
              ,'<input required name="name" lay-verify="required" placeholder="博主名称" value="" class="layui-input">'
            ,'</div>'
          ,'</li>'
          ,'<li class="layui-form-item">'
            ,'<label class="layui-form-label">博客地址</label>'
            ,'<div class="layui-input-block">'
              ,'<input required name="url" lay-verify="url" placeholder="博客地址" value="" class="layui-input">'
            ,'</div>'
          ,'</li>'
          ,'<li class="layui-form-item layui-form-text">'
            ,'<label class="layui-form-label">博主简介</label>'
            ,'<div class="layui-input-block layui-form-text">'
              ,'<textarea required name="desc" lay-verify="required" autocomplete="off" placeholder="博主简介" class="layui-textarea"></textarea>'
            ,'</div>'
          ,'</li>'
          ,'<li class="layui-form-item">'
            ,'<div class="layui-input-block">'
              ,'<button type="button" lay-submit lay-filter="pushLeader" class="layui-btn">提交博客</button>'
           ,'</div>'
          ,'</li>'
        ,'</ul>'].join('')
        ,success: function(layero, index){
          var image = layero.find('.fly-case-image')
          ,preview = $('#preview');
 
          form.on('submit(pushLeader)', function(data){
            fly.json('/leader/add', data.field, function(res){
              layer.close(index);
              layer.alert(res.msg, {
                icon: 1
              })
            });
          });
        }
      });
    }
    
    //点赞
    ,praise: function(othis){
      console.log("praise-------");
      var li = othis.parents('li')
      ,numElem = li.find('#praise-text')

      fly.json('/leader/vote', {
        id: li.data('id')
      }, function(res){
        numElem.html(res.praise + "个赞");
        if(othis.hasClass('raised')){
          othis.removeClass('raised');
        } else {
          othis.addClass('raised');
        }
      });
    }

    //查看点赞用户
    ,showPraise: function(othis){
      var li = othis.parents('li');
      if(othis.html() == 0) return layer.tips('该项目还没有收到赞', othis, {
        tips: 1
      });
      fly.json('/case/praise_user/', {
        id: li.data('id')
      }, function(res){
        var html = '';
        layer.open({
          type: 1
          ,title: '项目【'+ res.title + '】获得的赞'
          ,id: 'LAY_showPraise'
          ,shade: 0.8
          ,shadeClose: true
          ,area: '305px'
          ,skin: 'layer-ext-case'
          ,content: function(){
            layui.each(res.data, function(_, item){
              html += '<li><a href="/u/'+ 168*item.id +'/" target="_blank" title="'+ item.username +'"><img src="'+ item.avatar +'"></a></li>'
            });
            return '<ul class="layer-ext-ul">' + html + '</ul>';
          }()
        })
      });
    }
  };

  $('body').on('click', '.fly-case-active', function(){
    var othis = $(this), type = othis.data('type');
    active[type] && active[type].call(this, othis);
  });

  exports('leader', {});
});