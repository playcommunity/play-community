'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

var _Action2 = require('../Action');

var _Action3 = _interopRequireDefault(_Action2);

var _BlotFormatter = require('../../BlotFormatter');

var _BlotFormatter2 = _interopRequireDefault(_BlotFormatter);

var _DefaultAligner = require('./DefaultAligner');

var _DefaultAligner2 = _interopRequireDefault(_DefaultAligner);

var _Aligner = require('./Aligner');

var _Toolbar = require('./Toolbar');

var _DefaultToolbar = require('./DefaultToolbar');

var _DefaultToolbar2 = _interopRequireDefault(_DefaultToolbar);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

var AlignAction = function (_Action) {
  _inherits(AlignAction, _Action);

  function AlignAction(formatter) {
    _classCallCheck(this, AlignAction);

    var _this = _possibleConstructorReturn(this, (AlignAction.__proto__ || Object.getPrototypeOf(AlignAction)).call(this, formatter));

    _this.aligner = new _DefaultAligner2.default(formatter.options.align);
    _this.toolbar = new _DefaultToolbar2.default();
    return _this;
  }

  _createClass(AlignAction, [{
    key: 'onCreate',
    value: function onCreate() {
      var toolbar = this.toolbar.create(this.formatter, this.aligner);
      this.formatter.overlay.appendChild(toolbar);
    }
  }, {
    key: 'onDestroy',
    value: function onDestroy() {
      var toolbar = this.toolbar.getElement();
      if (!toolbar) {
        return;
      }

      this.formatter.overlay.removeChild(toolbar);
      this.toolbar.destroy();
    }
  }]);

  return AlignAction;
}(_Action3.default);

exports.default = AlignAction;