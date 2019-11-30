'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

var _deepmerge = require('deepmerge');

var _deepmerge2 = _interopRequireDefault(_deepmerge);

var _Options = require('./Options');

var _Options2 = _interopRequireDefault(_Options);

var _Action = require('./actions/Action');

var _Action2 = _interopRequireDefault(_Action);

var _BlotSpec = require('./specs/BlotSpec');

var _BlotSpec2 = _interopRequireDefault(_BlotSpec);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var dontMerge = function dontMerge(destination, source) {
  return source;
};

var BlotFormatter = function () {
  function BlotFormatter(quill) {
    var _this = this;

    var options = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

    _classCallCheck(this, BlotFormatter);

    this.onClick = function () {
      _this.hide();
    };

    this.quill = quill;
    this.options = (0, _deepmerge2.default)(_Options2.default, options, { arrayMerge: dontMerge });
    this.currentSpec = null;
    this.actions = [];
    this.overlay = document.createElement('div');
    this.overlay.classList.add(this.options.overlay.className);
    if (this.options.overlay.style) {
      Object.assign(this.overlay.style, this.options.overlay.style);
    }

    // disable native image resizing on firefox
    document.execCommand('enableObjectResizing', false, 'false'); // eslint-disable-line no-undef
    this.quill.root.parentNode.style.position = this.quill.root.parentNode.style.position || 'relative';

    this.quill.root.addEventListener('click', this.onClick);
    this.specs = this.options.specs.map(function (SpecClass) {
      return new SpecClass(_this);
    });
    this.specs.forEach(function (spec) {
      return spec.init();
    });
  }

  _createClass(BlotFormatter, [{
    key: 'show',
    value: function show(spec) {
      this.currentSpec = spec;
      this.currentSpec.setSelection();
      this.setUserSelect('none');
      this.quill.root.parentNode.appendChild(this.overlay);
      this.repositionOverlay();
      this.createActions(spec);
    }
  }, {
    key: 'hide',
    value: function hide() {
      if (!this.currentSpec) {
        return;
      }

      this.currentSpec.onHide();
      this.currentSpec = null;
      this.quill.root.parentNode.removeChild(this.overlay);
      this.overlay.style.setProperty('display', 'none');
      this.setUserSelect('');
      this.destroyActions();
    }
  }, {
    key: 'update',
    value: function update() {
      this.repositionOverlay();
      this.actions.forEach(function (action) {
        return action.onUpdate();
      });
    }
  }, {
    key: 'createActions',
    value: function createActions(spec) {
      var _this2 = this;

      this.actions = spec.getActions().map(function (ActionClass) {
        var action = new ActionClass(_this2);
        action.onCreate();
        return action;
      });
    }
  }, {
    key: 'destroyActions',
    value: function destroyActions() {
      this.actions.forEach(function (action) {
        return action.onDestroy();
      });
      this.actions = [];
    }
  }, {
    key: 'repositionOverlay',
    value: function repositionOverlay() {
      if (!this.currentSpec) {
        return;
      }

      var overlayTarget = this.currentSpec.getOverlayElement();
      if (!overlayTarget) {
        return;
      }

      var parent = this.quill.root.parentNode;
      var specRect = overlayTarget.getBoundingClientRect();
      var parentRect = parent.getBoundingClientRect();

      Object.assign(this.overlay.style, {
        display: 'block',
        left: specRect.left - parentRect.left - 1 + parent.scrollLeft + 'px',
        top: specRect.top - parentRect.top + parent.scrollTop + 'px',
        width: specRect.width + 'px',
        height: specRect.height + 'px'
      });
    }
  }, {
    key: 'setUserSelect',
    value: function setUserSelect(value) {
      var _this3 = this;

      var props = ['userSelect', 'mozUserSelect', 'webkitUserSelect', 'msUserSelect'];

      props.forEach(function (prop) {
        // set on contenteditable element and <html>
        _this3.quill.root.style.setProperty(prop, value);
        if (document.documentElement) {
          document.documentElement.style.setProperty(prop, value);
        }
      });
    }
  }]);

  return BlotFormatter;
}();

exports.default = BlotFormatter;