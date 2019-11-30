'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

var _Action2 = require('./Action');

var _Action3 = _interopRequireDefault(_Action2);

var _BlotFormatter = require('../BlotFormatter');

var _BlotFormatter2 = _interopRequireDefault(_BlotFormatter);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

var ResizeAction = function (_Action) {
  _inherits(ResizeAction, _Action);

  function ResizeAction(formatter) {
    _classCallCheck(this, ResizeAction);

    var _this = _possibleConstructorReturn(this, (ResizeAction.__proto__ || Object.getPrototypeOf(ResizeAction)).call(this, formatter));

    _this.onMouseDown = function (event) {
      if (!(event.target instanceof HTMLElement)) {
        return;
      }
      _this.dragHandle = event.target;
      _this.setCursor(_this.dragHandle.style.cursor);

      if (!_this.formatter.currentSpec) {
        return;
      }

      var target = _this.formatter.currentSpec.getTargetElement();
      if (!target) {
        return;
      }

      var rect = target.getBoundingClientRect();

      _this.dragStartX = event.clientX;
      _this.preDragWidth = rect.width;
      _this.targetRatio = rect.height / rect.width;

      document.addEventListener('mousemove', _this.onDrag);
      document.addEventListener('mouseup', _this.onMouseUp);
    };

    _this.onDrag = function (event) {
      if (!_this.formatter.currentSpec) {
        return;
      }

      var target = _this.formatter.currentSpec.getTargetElement();
      if (!target) {
        return;
      }

      var deltaX = event.clientX - _this.dragStartX;
      var newWidth = 0;

      if (_this.dragHandle === _this.topLeftHandle || _this.dragHandle === _this.bottomLeftHandle) {
        newWidth = Math.round(_this.preDragWidth - deltaX);
      } else {
        newWidth = Math.round(_this.preDragWidth + deltaX);
      }

      var newHeight = _this.targetRatio * newWidth;

      target.setAttribute('width', '' + newWidth);
      target.setAttribute('height', '' + newHeight);

      _this.formatter.update();
    };

    _this.onMouseUp = function () {
      _this.setCursor('');
      document.removeEventListener('mousemove', _this.onDrag);
      document.removeEventListener('mouseup', _this.onMouseUp);
    };

    _this.topLeftHandle = _this.createHandle('top-left', 'nwse-resize');
    _this.topRightHandle = _this.createHandle('top-right', 'nesw-resize');
    _this.bottomRightHandle = _this.createHandle('bottom-right', 'nwse-resize');
    _this.bottomLeftHandle = _this.createHandle('bottom-left', 'nesw-resize');
    _this.dragHandle = null;
    _this.dragStartX = 0;
    _this.preDragWidth = 0;
    _this.targetRatio = 0;
    return _this;
  }

  _createClass(ResizeAction, [{
    key: 'onCreate',
    value: function onCreate() {
      this.formatter.overlay.appendChild(this.topLeftHandle);
      this.formatter.overlay.appendChild(this.topRightHandle);
      this.formatter.overlay.appendChild(this.bottomRightHandle);
      this.formatter.overlay.appendChild(this.bottomLeftHandle);

      this.repositionHandles(this.formatter.options.resize.handleStyle);
    }
  }, {
    key: 'onDestroy',
    value: function onDestroy() {
      this.setCursor('');
      this.formatter.overlay.removeChild(this.topLeftHandle);
      this.formatter.overlay.removeChild(this.topRightHandle);
      this.formatter.overlay.removeChild(this.bottomRightHandle);
      this.formatter.overlay.removeChild(this.bottomLeftHandle);
    }
  }, {
    key: 'createHandle',
    value: function createHandle(position, cursor) {
      var box = document.createElement('div');
      box.classList.add(this.formatter.options.resize.handleClassName);
      box.setAttribute('data-position', position);
      box.style.cursor = cursor;

      if (this.formatter.options.resize.handleStyle) {
        Object.assign(box.style, this.formatter.options.resize.handleStyle);
      }

      box.addEventListener('mousedown', this.onMouseDown);

      return box;
    }
  }, {
    key: 'repositionHandles',
    value: function repositionHandles(handleStyle) {
      var handleXOffset = '0px';
      var handleYOffset = '0px';
      if (handleStyle) {
        if (handleStyle.width) {
          handleXOffset = -parseFloat(handleStyle.width) / 2 + 'px';
        }
        if (handleStyle.height) {
          handleYOffset = -parseFloat(handleStyle.height) / 2 + 'px';
        }
      }

      Object.assign(this.topLeftHandle.style, { left: handleXOffset, top: handleYOffset });
      Object.assign(this.topRightHandle.style, { right: handleXOffset, top: handleYOffset });
      Object.assign(this.bottomRightHandle.style, { right: handleXOffset, bottom: handleYOffset });
      Object.assign(this.bottomLeftHandle.style, { left: handleXOffset, bottom: handleYOffset });
    }
  }, {
    key: 'setCursor',
    value: function setCursor(value) {
      if (document.body) {
        document.body.style.cursor = value;
      }

      if (this.formatter.currentSpec) {
        var target = this.formatter.currentSpec.getOverlayElement();
        if (target) {
          target.style.cursor = value;
        }
      }
    }
  }]);

  return ResizeAction;
}(_Action3.default);

exports.default = ResizeAction;