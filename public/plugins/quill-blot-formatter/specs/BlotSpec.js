'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

var _BlotFormatter = require('../BlotFormatter');

var _BlotFormatter2 = _interopRequireDefault(_BlotFormatter);

var _Action = require('../actions/Action');

var _Action2 = _interopRequireDefault(_Action);

var _AlignAction = require('../actions/align/AlignAction');

var _AlignAction2 = _interopRequireDefault(_AlignAction);

var _ResizeAction = require('../actions/ResizeAction');

var _ResizeAction2 = _interopRequireDefault(_ResizeAction);

var _DeleteAction = require('../actions/DeleteAction');

var _DeleteAction2 = _interopRequireDefault(_DeleteAction);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var BlotSpec = function () {
  function BlotSpec(formatter) {
    _classCallCheck(this, BlotSpec);

    this.formatter = formatter;
  }

  _createClass(BlotSpec, [{
    key: 'init',
    value: function init() {}
  }, {
    key: 'getActions',
    value: function getActions() {
      return [_AlignAction2.default, _ResizeAction2.default, _DeleteAction2.default];
    }
  }, {
    key: 'getTargetElement',
    value: function getTargetElement() {
      return null;
    }
  }, {
    key: 'getOverlayElement',
    value: function getOverlayElement() {
      return this.getTargetElement();
    }
  }, {
    key: 'setSelection',
    value: function setSelection() {
      this.formatter.quill.setSelection(null);
    }
  }, {
    key: 'onHide',
    value: function onHide() {}
  }]);

  return BlotSpec;
}();

exports.default = BlotSpec;