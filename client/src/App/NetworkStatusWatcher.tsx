/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useRef} from 'react';
import {notificationsStore} from 'modules/stores/notifications';

const NetworkStatusWatcher: React.FC = () => {
  const notificationRef = useRef<{remove: () => void} | null>(null);

  useEffect(() => {
    async function handleDisconnection() {
      notificationRef.current = {
        remove: notificationsStore.displayNotification({
          kind: 'info',
          title: 'Internet connection lost',
          isDismissable: false,
        }),
      };
    }

    if (!window.navigator.onLine) {
      handleDisconnection();
    }

    window.addEventListener('offline', handleDisconnection);

    return () => {
      window.removeEventListener('offline', handleDisconnection);
    };
  }, []);

  useEffect(() => {
    function handleReconnection() {
      notificationRef.current?.remove();
    }

    window.addEventListener('online', handleReconnection);

    return () => {
      window.removeEventListener('online', handleReconnection);
    };
  }, []);

  return null;
};

export {NetworkStatusWatcher};
