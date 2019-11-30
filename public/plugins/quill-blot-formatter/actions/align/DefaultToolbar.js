'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

var _Toolbar = require('./Toolbar');

var _Aligner = require('./Aligner');

var _BlotFormatter = require('../../BlotFormatter');

var _BlotFormatter2 = _interopRequireDefault(_BlotFormatter);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var DefaultToolbar = function () {
  function DefaultToolbar() {
    _classCallCheck(this, DefaultToolbar);

    this.toolbar = null;
    this.buttons = [];
  }

  _createClass(DefaultToolbar, [{
    key: 'create',
    value: function create(formatter, aligner) {
      var toolbar = document.createElement('div');
      toolbar.classList.add(formatter.options.align.toolbar.mainClassName);
      this.addToolbarStyle(formatter, toolbar);
      this.addButtons(formatter, toolbar, aligner);

      this.toolbar = toolbar;
      return this.toolbar;
    }
  }, {
    key: 'destroy',
    value: function destroy() {
      this.toolbar = null;
      this.buttons = [];
    }
  }, {
    key: 'getElement',
    value: function getElement() {
      return this.toolbar;
    }
  }, {
    key: 'addToolbarStyle',
    value: function addToolbarStyle(formatter, toolbar) {
      if (formatter.options.align.toolbar.mainStyle) {
        Object.assign(toolbar.style, formatter.options.align.toolbar.mainStyle);
      }
    }
  }, {
    key: 'addButtonStyle',
    value: function addButtonStyle(button, index, formatter) {
      if (formatter.options.align.toolbar.buttonStyle) {
        Object.assign(button.style, formatter.options.align.toolbar.buttonStyle);
        if (index > 0) {
          button.style.borderLeftWidth = '0'; // eslint-disable-line no-param-reassign
        }
      }

      if (formatter.options.align.toolbar.svgStyle) {
        Object.assign(button.children[0].style, formatter.options.align.toolbar.svgStyle);
      }
    }
  }, {
    key: 'addButtons',
    value: function addButtons(formatter, toolbar, aligner) {
      var _this = this;

      aligner.getAlignments().forEach(function (alignment, i) {
        var button = document.createElement('span');
        button.classList.add(formatter.options.align.toolbar.buttonClassName);
        button.innerHTML = alignment.icon;
        button.addEventListener('click', function () {
          _this.onButtonClick(button, formatter, alignment, aligner);
        });
        _this.preselectButton(button, alignment, formatter, aligner);
        _this.addButtonStyle(button, i, formatter);
        _this.buttons.push(button);
        toolbar.appendChild(button);
      });
    }
  }, {
    key: 'preselectButton',
    value: function preselectButton(button, alignment, formatter, aligner) {
      if (!formatter.currentSpec) {
        return;
      }

      var target = formatter.currentSpec.getTargetElement();
      if (!target) {
        return;
      }

      if (aligner.isAligned(target, alignment)) {
        this.selectButton(formatter, button);
      }
    }
  }, {
    key: 'onButtonClick',
    value: function onButtonClick(button, formatter, alignment, aligner) {
      if (!formatter.currentSpec) {
        return;
      }

      var target = formatter.currentSpec.getTargetElement();
      if (!target) {
        return;
      }

      this.clickButton(button, target, formatter, alignment, aligner);
    }
  }, {
    key: 'clickButton',
    value: function clickButton(button, alignTarget, formatter, alignment, aligner) {
      var _this2 = this;

      this.buttons.forEach(function (b) {
        _this2.deselectButton(formatter, b);
      });
      if (aligner.isAligned(alignTarget, alignment)) {
        if (formatter.options.align.toolbar.allowDeselect) {
          aligner.clear(alignTarget);
        } else {
          this.selectButton(formatter, button);
        }
      } else {
        this.selectButton(formatter, button);
        alignment.apply(alignTarget);
      }

      formatter.update();
    }
  }, {
    key: 'selectButton',
    value: function selectButton(formatter, button) {
      button.classList.add('is-selected');
      if (formatter.options.align.toolbar.addButtonSelectStyle) {
        button.style.setProperty('filter', 'invert(20%)');
      }
    }
  }, {
    key: 'deselectButton',
    value: function deselectButton(formatter, button) {
      button.classList.remove('is-selected');
      if (formatter.options.align.toolbar.addButtonSelectStyle) {
        button.style.removeProperty('filter');
      }
    }
  }]);

  return DefaultToolbar;
}();

exports.default = DefaultToolbar;