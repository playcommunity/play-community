'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _BlotSpec = require('./specs/BlotSpec');

var _BlotSpec2 = _interopRequireDefault(_BlotSpec);

var _ImageSpec = require('./specs/ImageSpec');

var _ImageSpec2 = _interopRequireDefault(_ImageSpec);

var _IframeVideoSpec = require('./specs/IframeVideoSpec');

var _IframeVideoSpec2 = _interopRequireDefault(_IframeVideoSpec);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

var DefaultOptions = {
  specs: [_ImageSpec2.default, _IframeVideoSpec2.default],
  overlay: {
    className: 'blot-formatter__overlay',
    style: {
      position: 'absolute',
      boxSizing: 'border-box',
      border: '1px dashed #444'
    }
  },
  align: {
    attribute: 'data-align',
    aligner: {
      applyStyle: true
    },
    icons: {
      left: '\n        <svg viewbox="0 0 18 18">\n          <line class="ql-stroke" x1="3" x2="15" y1="9" y2="9"></line>\n          <line class="ql-stroke" x1="3" x2="13" y1="14" y2="14"></line>\n          <line class="ql-stroke" x1="3" x2="9" y1="4" y2="4"></line>\n        </svg>\n      ',
      center: '\n        <svg viewbox="0 0 18 18">\n           <line class="ql-stroke" x1="15" x2="3" y1="9" y2="9"></line>\n          <line class="ql-stroke" x1="14" x2="4" y1="14" y2="14"></line>\n          <line class="ql-stroke" x1="12" x2="6" y1="4" y2="4"></line>\n        </svg>\n      ',
      right: '\n        <svg viewbox="0 0 18 18">\n          <line class="ql-stroke" x1="15" x2="3" y1="9" y2="9"></line>\n          <line class="ql-stroke" x1="15" x2="5" y1="14" y2="14"></line>\n          <line class="ql-stroke" x1="15" x2="9" y1="4" y2="4"></line>\n        </svg>\n      '
    },
    toolbar: {
      allowDeselect: true,
      mainClassName: 'blot-formatter__toolbar',
      mainStyle: {
        position: 'absolute',
        top: '-12px',
        right: '0',
        left: '0',
        height: '0',
        minWidth: '100px',
        font: '12px/1.0 Arial, Helvetica, sans-serif',
        textAlign: 'center',
        color: '#333',
        boxSizing: 'border-box',
        cursor: 'default',
        zIndex: '1'
      },
      buttonClassName: 'blot-formatter__toolbar-button',
      addButtonSelectStyle: true,
      buttonStyle: {
        display: 'inline-block',
        width: '24px',
        height: '24px',
        background: 'white',
        border: '1px solid #999',
        verticalAlign: 'middle'
      },
      svgStyle: {
        display: 'inline-block',
        width: '24px',
        height: '24px',
        background: 'white',
        border: '1px solid #999',
        verticalAlign: 'middle'
      }
    }
  },
  resize: {
    handleClassName: 'blot-formatter__resize-handle',
    handleStyle: {
      position: 'absolute',
      height: '12px',
      width: '12px',
      backgroundColor: 'white',
      border: '1px solid #777',
      boxSizing: 'border-box',
      opacity: '0.80'
    }
  }
};

exports.default = DefaultOptions;