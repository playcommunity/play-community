/**

 @Name: 求解板块

 */
 
layui.define(['laypage', 'fly'], function(exports){

  var $ = layui.jquery;
  var layer = layui.layer;
  var util = layui.util;
  var laytpl = layui.laytpl;
  var form = layui.form;
  var laypage = layui.laypage;
  var fly = layui.fly;
  
  var gather = {}, dom = {
    jieda: $('#jieda')
    ,content: $('#L_content')
    ,jiedaCount: $('#jiedaCount')
  };

  //提交回答
  fly.form['/article/reply'] = function(data, required){
    var tpl = '<li>\
      <div class="detail-about detail-about-reply">\
        <a class="jie-user" href="/user/">\
          <img src="{{= d.user.avatar}}" alt="{{= d.user.username}}">\
          <cite>{{d.user.username}}</cite>\
        </a>\
        <div class="detail-hits">\
          <span>刚刚</span>\
        </div>\
      </div>\
      <div class="detail-body jieda-body">\
        {{ d.content}}\
      </div>\
    </li>'
    data.content = fly.content(data.content);
    laytpl(tpl).render($.extend(data, {
      user: layui.cache.user
    }), function(html){
      required[0].value = '';
      dom.jieda.find('.fly-none').remove();
      dom.jieda.append(html);
      
      var count = dom.jiedaCount.text()|0;
      dom.jiedaCount.html(++count);
    });
  };

  //求解管理
  gather.jieAdmin = {
    //删求解
    del: function(div){
      layer.confirm('确认删除该求解么？', function(index){
        layer.close(index);
        fly.json('/api/jie-delete/', {
          id: div.data('id')
        }, function(res){
          if(res.status === 0){
            location.href = '/jie/';
          } else {
            layer.msg(res.msg);
          }
        });
      });
    }
    
    //设置置顶、状态
    ,set: function(div){
      var othis = $(this);
      fly.json('/api/jie-set/', {
        id: div.data('id')
        ,rank: othis.attr('rank')
        ,field: othis.attr('field')
      }, function(res){
        if(res.status === 0){
          location.reload();
        }
      });
    }

    //编辑
    ,edit: function(div){
    }

    //收藏
    ,collect: function(div){
      var othis = $(this), type = othis.data('type'), token = othis.data('token');
      fly.json('/user/collect', {
        resType: 'article',
        resId: div.data('id'),
        csrfToken: token
      }, function(res){
        if(type === 'add'){
          othis.data('type', 'remove').html('取消收藏').addClass('layui-btn-danger');
        } else if(type === 'remove'){
          othis.data('type', 'add').html('收藏').removeClass('layui-btn-danger');
        }
      });
    }
  };

  $('body').on('click', '.jie-admin', function(){
    var othis = $(this), type = othis.attr('type');
    gather.jieAdmin[type].call(this, othis.parent());
  });

  /*
  layui.use('upload', function(upload){
    var token = $('#LAY-upload-image').data('token');
    layui.upload({
      elem: '#LAY-upload-image'
      ,method: 'post'
      ,url: '/resource/owner/editor?csrfToken=' + token
      ,success: function(res){
        var range = quill.getSelection(true);
        var Delta = Quill.import('delta');
        quill.updateContents(
          new Delta().retain(range.index)
             .delete(range.length)
             .insert({ image: res.url })
          , 'user');
      }
      ,error: function(){
        layer.msg('error');
      }
    });
  });
  */

  //异步渲染
  var asyncRender = function(){
    var div = $('.fly-detail-hint'), jieAdmin = $('#LAY_jieAdmin');
    //查询帖子是否收藏
    if(jieAdmin[0] && layui.cache.user.uid != -1){
      fly.json('/collection/find/', {
        cid: div.data('id')
      }, function(res){
        jieAdmin.append('<span class="layui-btn layui-btn-mini jie-admin '+ (res.data.collection ? 'layui-btn-danger' : '') +'" type="collect" data-type="'+ (res.data.collection ? 'remove' : 'add') +'">'+ (res.data.collection ? '取消收藏' : '收藏') +'</span>');
      });
    }
  }();

  //解答操作
  gather.jiedaActive = {
    zan: function(li){ //赞
      var othis = $(this), ok = othis.hasClass('zanok'), token = othis.data('token');
      fly.json('/article/reply/vote', {
        up: !ok
        ,aid: li.data('aid')
        ,rid: li.data('rid')
        ,csrfToken: token
      }, function(res){
        if(res.status === 0){
          var zans = othis.find('em').html()|0;
          othis[ok ? 'removeClass' : 'addClass']('zanok');
          othis.find('em').html(ok ? (--zans) : (++zans));
        } else {
          layer.msg(res.msg);
        }
      });
    }
    ,reply: function(li){ //回复
      var aite = '@'+ li.find('.jie-user cite i').text().replace(/\s/g, '');
      var uid = li.data('uid');
      var Delta = Quill.import('delta');
      var range = quill.getSelection(true);
      quill.updateContents(
          new Delta().retain(range.index).insert(aite),
          'user'
      );
      var atInput = $('#at-input');
      if(atInput.val() == ''){
        atInput.val(uid);
      }else if(atInput.val().indexOf(uid) < 0){
        atInput.val(atInput.val() + ',' + uid);
      }
    }
    ,accept: function(li){ //采纳
      var othis = $(this);
      layer.confirm('是否采纳该回答为最佳答案？', function(index){
        layer.close(index);
        fly.json('/api/jieda-accept/', {
          id: li.data('id')
        }, function(res){
          if(res.status === 0){
            $('.jieda-accept').remove();
            li.addClass('jieda-daan');
            li.find('.detail-about').append('<i class="iconfont icon-caina" title="最佳答案"></i>');
          } else {
            layer.msg(res.msg);
          }
        });
      });
    }
    ,edit: function(li){ //编辑
      fly.json('/article/reply/edit', {
        aid: li.data('aid'),
        rid: li.data('rid')
      }, function(res){
        var data = res.rows;
        layer.prompt({
         formType: 2
         ,value: data.content
         ,maxlength: 100000
        }, function(value, index){
          fly.json('/article/reply/edit', {
            aid: li.data('aid')
            ,rid: li.data('rid')
            ,content: value
            ,csrfToken: li.data('token')
          }, function(res){
            layer.close(index);
            li.find('.detail-body').html(fly.content(value));
          });
        });
      }, {type: 'get'});
    }
    ,del: function(li){ //删除
      layer.confirm('确认删除该回答么？', function(index){
        layer.close(index);
        fly.json('/article/reply/remove', {
          aid: li.data('aid')
          ,rid: li.data('rid')
          ,csrfToken: li.data('token')
        }, function(res){
          if(res.status === 0){
            var count = dom.jiedaCount.text()|0;
            dom.jiedaCount.html(--count);
            li.remove();
            //如果删除了最佳答案
            if(li.hasClass('jieda-daan')){
              $('.jie-status').removeClass('jie-status-ok').text('求解中');
            }
          } else {
            layer.msg(res.msg);
          }
        });
      });    
    }
  };
  $('.jieda-reply span').on('click', function(){
    var othis = $(this), type = othis.attr('type');
    gather.jiedaActive[type].call(this, othis.parents('li'));
  });

  exports('jie', null);
});