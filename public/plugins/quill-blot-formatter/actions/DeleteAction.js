'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

var _quill = require('quill');

var _quill2 = _interopRequireDefault(_quill);

var _Action2 = require('./Action');

var _Action3 = _interopRequireDefault(_Action2);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

var DeleteAction = function (_Action) {
  _inherits(DeleteAction, _Action);

  function DeleteAction() {
    var _ref;

    var _temp, _this, _ret;

    _classCallCheck(this, DeleteAction);

    for (var _len = arguments.length, args = Array(_len), _key = 0; _key < _len; _key++) {
      args[_key] = arguments[_key];
    }

    return _ret = (_temp = (_this = _possibleConstructorReturn(this, (_ref = DeleteAction.__proto__ || Object.getPrototypeOf(DeleteAction)).call.apply(_ref, [this].concat(args))), _this), _this.onKeyUp = function (e) {
      if (!_this.formatter.currentSpec) {
        return;
      }

      // delete or backspace
      if (e.keyCode === 46 || e.keyCode === 8) {
        var blot = _quill2.default.find(_this.formatter.currentSpec.getTargetElement());
        if (blot) {
          blot.deleteAt(0);
        }
        _this.formatter.hide();
      }
    }, _temp), _possibleConstructorReturn(_this, _ret);
  }

  _createClass(DeleteAction, [{
    key: 'onCreate',
    value: function onCreate() {
      document.addEventListener('keyup', this.onKeyUp, true);
      this.formatter.quill.root.addEventListener('input', this.onKeyUp, true);
    }
  }, {
    key: 'onDestroy',
    value: function onDestroy() {
      document.removeEventListener('keyup', this.onKeyUp);
      this.formatter.quill.root.removeEventListener('input', this.onKeyUp);
    }
  }]);

  return DeleteAction;
}(_Action3.default);

exports.default = DeleteAction;