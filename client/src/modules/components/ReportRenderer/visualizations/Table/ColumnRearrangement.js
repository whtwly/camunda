/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import processRawData from './processRawData';

import './ColumnRearrangement.scss';

export default class ColumnRearrangement extends React.Component {
  render() {
    const {updateReport, report} = this.props;
    // not raw data report
    if (report.combined || report.data.view.properties[0] !== 'rawData' || !updateReport) {
      return this.props.children;
    }
    return (
      <div className="ColumnRearrangement" onMouseDown={this.handleMouseDown}>
        {this.props.children}
      </div>
    );
  }

  handleMouseDown = (evt) => {
    if (evt.target.classList.contains('resizer')) {
      return;
    }

    const columnHeader = evt.target.closest('.Table .tableHeader:not(.placeholder)');

    if (columnHeader) {
      this.dragIdx = Array.from(columnHeader.parentNode.childNodes).indexOf(columnHeader);
      forColumn(this.dragIdx)
        .do(({classList}) => classList.add('ColumnRearrangement__draggedColumn'))
        .usingEvent(evt);

      this.preview = createDragPreview(this.dragIdx, evt);

      document.addEventListener('mousemove', this.handleMouseMove);
      document.addEventListener('mouseup', this.handleMouseUp);
    }
  };

  handleMouseMove = (evt) => {
    if (!this.preview.parentNode) {
      document.body.appendChild(this.preview);
    }

    removeHighlights();

    const targetIdx = this.processDrag(evt);

    if (typeof targetIdx !== 'undefined') {
      forColumn(targetIdx)
        .do(({classList}) => classList.add(`ColumnRearrangement__dropTarget--left`))
        .usingEvent(evt);
      forColumn(targetIdx + 1)
        .do(({classList}) => classList.add(`ColumnRearrangement__dropTarget--right`))
        .usingEvent(evt);
    }
  };

  handleMouseUp = (evt) => {
    removeHighlights(true);

    const targetIdx = this.processDrag(evt);

    if (typeof targetIdx !== 'undefined') {
      const {reportType} = this.props.report;
      const list = processRawData[reportType](this.props).head.map((el) => el.id);

      // add the column at the specified position
      list.splice(targetIdx + 1, 0, list[this.dragIdx]);

      // remove the original column
      list.splice(this.dragIdx + (this.dragIdx > targetIdx), 1);

      this.props.updateReport({configuration: {tableColumns: {columnOrder: {$set: list}}}});
    }

    document.removeEventListener('mousemove', this.handleMouseMove);
    document.removeEventListener('mouseup', this.handleMouseUp);
  };

  processDrag = (evt) => {
    const elem = evt.target.closest('.Table td, .Table .tableHeader');

    if (elem) {
      let idx = Array.from(elem.parentNode.childNodes).indexOf(elem);

      if (evt.offsetX < elem.clientWidth / 2) {
        idx--;
      }

      return idx;
    }
  };
}

function forColumn(columnIdx) {
  return {
    do: (fct) => {
      if (columnIdx === 'all') {
        cellsForColumn(document, '1n').forEach(fct);
      } else {
        return {
          usingEvent: ({target}) => {
            const table = target.closest('.Table');

            if (table) {
              cellsForColumn(table, columnIdx + 1).forEach(fct);
            }
          },
        };
      }
    },
  };
}

function cellsForColumn(target, matcher) {
  return target.querySelectorAll(
    `.Table tbody tr td:nth-child(${matcher}),.Table thead tr .tableHeader:nth-child(${matcher})`
  );
}

function removeHighlights(alsoRemoveDraggedColumnStyle) {
  forColumn('all').do(({classList}) =>
    [
      'ColumnRearrangement__dropTarget--left',
      'ColumnRearrangement__dropTarget--right',
      alsoRemoveDraggedColumnStyle && 'ColumnRearrangement__draggedColumn',
    ].forEach((cssClass) => classList.remove(cssClass))
  );
}

function createDragPreview(idx, evt) {
  // create a copy of the table, hide all irrelevant stuff so that only the column remains
  // then make this column follow mouse movements
  const preview = evt.target.closest('.Table').cloneNode(true);
  preview.classList.add('ColumnRearrangement__dragPreview');
  cellsForColumn(preview, `-n+${idx}`).forEach(({style}) => (style.display = 'none'));
  cellsForColumn(preview, idx + 1).forEach(({style}) => {
    style.width = '250px';
    style.maxWidth = '250px';
  });
  cellsForColumn(preview, `n+${idx + 2}`).forEach(({style}) => (style.display = 'none'));

  preview.style.top = evt.pageY + 'px';
  preview.style.left = evt.pageX + 'px';

  function update(evt) {
    preview.style.top = evt.pageY + 'px';
    preview.style.left = evt.pageX + 'px';
  }

  function stopDrag() {
    if (preview.parentNode === document.body) {
      document.body.removeChild(preview);
    }
    document.body.removeEventListener('mousemove', update);
    document.body.removeEventListener('mouseup', stopDrag);
  }

  document.body.addEventListener('mousemove', update);
  document.body.addEventListener('mouseup', stopDrag);

  return preview;
}
