'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

var _BlotSpec2 = require('./BlotSpec');

var _BlotSpec3 = _interopRequireDefault(_BlotSpec2);

var _BlotFormatter = require('../BlotFormatter');

var _BlotFormatter2 = _interopRequireDefault(_BlotFormatter);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

var MOUSE_ENTER_ATTRIBUTE = 'data-blot-formatter-unclickable-bound';
var PROXY_IMAGE_CLASS = 'blot-formatter__proxy-image';

var UnclickableBlotSpec = function (_BlotSpec) {
  _inherits(UnclickableBlotSpec, _BlotSpec);

  function UnclickableBlotSpec(formatter, selector) {
    _classCallCheck(this, UnclickableBlotSpec);

    var _this = _possibleConstructorReturn(this, (UnclickableBlotSpec.__proto__ || Object.getPrototypeOf(UnclickableBlotSpec)).call(this, formatter));

    _this.onTextChange = function () {
      Array.from(document.querySelectorAll(_this.selector + ':not([' + MOUSE_ENTER_ATTRIBUTE + '])')).forEach(function (unclickable) {
        unclickable.setAttribute(MOUSE_ENTER_ATTRIBUTE, 'true');
        unclickable.addEventListener('mouseenter', _this.onMouseEnter);
      });
    };

    _this.onMouseEnter = function (event) {
      var unclickable = event.target;
      if (!(unclickable instanceof HTMLElement)) {
        return;
      }

      _this.nextUnclickable = unclickable;
      _this.repositionProxyImage(_this.nextUnclickable);
    };

    _this.onProxyImageClick = function () {
      _this.unclickable = _this.nextUnclickable;
      _this.nextUnclickable = null;
      _this.formatter.show(_this);
      _this.hideProxyImage();
    };

    _this.selector = selector;
    _this.unclickable = null;
    _this.nextUnclickable = null;
    return _this;
  }

  _createClass(UnclickableBlotSpec, [{
    key: 'init',
    value: function init() {
      if (document.body) {
        /*
        it's important that this is attached to the body instead of the root quill element.
        this prevents the click event from overlapping with ImageSpec
         */
        document.body.appendChild(this.createProxyImage());
      }

      this.hideProxyImage();
      this.proxyImage.addEventListener('click', this.onProxyImageClick);
      this.formatter.quill.on('text-change', this.onTextChange);
    }
  }, {
    key: 'getTargetElement',
    value: function getTargetElement() {
      return this.unclickable;
    }
  }, {
    key: 'getOverlayElement',
    value: function getOverlayElement() {
      return this.unclickable;
    }
  }, {
    key: 'onHide',
    value: function onHide() {
      this.hideProxyImage();
      this.nextUnclickable = null;
      this.unclickable = null;
    }
  }, {
    key: 'createProxyImage',
    value: function createProxyImage() {
      var canvas = document.createElement('canvas');
      var context = canvas.getContext('2d');
      context.globalAlpha = 0;
      context.fillRect(0, 0, 1, 1);

      this.proxyImage = document.createElement('img');
      this.proxyImage.src = canvas.toDataURL('image/png');
      this.proxyImage.classList.add(PROXY_IMAGE_CLASS);

      Object.assign(this.proxyImage.style, {
        position: 'absolute',
        margin: '0'
      });

      return this.proxyImage;
    }
  }, {
    key: 'hideProxyImage',
    value: function hideProxyImage() {
      Object.assign(this.proxyImage.style, {
        display: 'none'
      });
    }
  }, {
    key: 'repositionProxyImage',
    value: function repositionProxyImage(unclickable) {
      var rect = unclickable.getBoundingClientRect();

      Object.assign(this.proxyImage.style, {
        display: 'block',
        left: rect.left + window.pageXOffset + 'px',
        top: rect.top + window.pageYOffset + 'px',
        width: rect.width + 'px',
        height: rect.height + 'px'
      });
    }
  }]);

  return UnclickableBlotSpec;
}(_BlotSpec3.default);

exports.default = UnclickableBlotSpec;