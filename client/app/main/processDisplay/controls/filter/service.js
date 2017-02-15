import {dispatchAction} from 'view-utils';
import {createOpenDateFilterModalAction,
        createCloseDateFilterModalAction,
        createCreateStartDateFilterAction} from './reducer';

export function openModal() {
  dispatchAction(createOpenDateFilterModalAction());
}

export function closeModal() {
  dispatchAction(createCloseDateFilterModalAction());
}

export function createStartDateFilter(startDate, endDate) {
  dispatchAction(createCreateStartDateFilterAction(startDate, endDate));
}

export function formatDate(dateObj) {
  return dateObj.toISOString().substr(0, 10);
}
