'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _UnclickableBlotSpec2 = require('./UnclickableBlotSpec');

var _UnclickableBlotSpec3 = _interopRequireDefault(_UnclickableBlotSpec2);

var _BlotFormatter = require('../BlotFormatter');

var _BlotFormatter2 = _interopRequireDefault(_BlotFormatter);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

var IframeVideoSpec = function (_UnclickableBlotSpec) {
  _inherits(IframeVideoSpec, _UnclickableBlotSpec);

  function IframeVideoSpec(formatter) {
    _classCallCheck(this, IframeVideoSpec);

    return _possibleConstructorReturn(this, (IframeVideoSpec.__proto__ || Object.getPrototypeOf(IframeVideoSpec)).call(this, formatter, 'iframe.ql-video'));
  }

  return IframeVideoSpec;
}(_UnclickableBlotSpec3.default);

exports.default = IframeVideoSpec;