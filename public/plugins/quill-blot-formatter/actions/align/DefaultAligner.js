'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

var _Aligner = require('./Aligner');

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var LEFT_ALIGN = 'left';
var CENTER_ALIGN = 'center';
var RIGHT_ALIGN = 'right';

var DefaultAligner = function () {
  function DefaultAligner(options) {
    var _this = this,
        _alignments;

    _classCallCheck(this, DefaultAligner);

    this.applyStyle = options.aligner.applyStyle;
    this.alignAttribute = options.attribute;
    this.alignments = (_alignments = {}, _defineProperty(_alignments, LEFT_ALIGN, {
      name: LEFT_ALIGN,
      icon: options.icons.left,
      apply: function apply(el) {
        _this.setAlignment(el, LEFT_ALIGN);
        _this.setStyle(el, 'inline', 'left', '0 1em 1em 0');
      }
    }), _defineProperty(_alignments, CENTER_ALIGN, {
      name: CENTER_ALIGN,
      icon: options.icons.center,
      apply: function apply(el) {
        _this.setAlignment(el, CENTER_ALIGN);
        _this.setStyle(el, 'block', null, 'auto');
      }
    }), _defineProperty(_alignments, RIGHT_ALIGN, {
      name: RIGHT_ALIGN,
      icon: options.icons.right,
      apply: function apply(el) {
        _this.setAlignment(el, RIGHT_ALIGN);
        _this.setStyle(el, 'inline', 'right', '0 0 1em 1em');
      }
    }), _alignments);
  }

  _createClass(DefaultAligner, [{
    key: 'getAlignments',
    value: function getAlignments() {
      var _this2 = this;

      return Object.keys(this.alignments).map(function (k) {
        return _this2.alignments[k];
      });
    }
  }, {
    key: 'clear',
    value: function clear(el) {
      el.removeAttribute(this.alignAttribute);
      this.setStyle(el, null, null, null);
    }
  }, {
    key: 'isAligned',
    value: function isAligned(el, alignment) {
      return el.getAttribute(this.alignAttribute) === alignment.name;
    }
  }, {
    key: 'setAlignment',
    value: function setAlignment(el, value) {
      el.setAttribute(this.alignAttribute, value);
    }
  }, {
    key: 'setStyle',
    value: function setStyle(el, display, float, margin) {
      if (this.applyStyle) {
        el.style.setProperty('display', display);
        el.style.setProperty('float', float);
        el.style.setProperty('margin', margin);
      }
    }
  }]);

  return DefaultAligner;
}();

exports.default = DefaultAligner;