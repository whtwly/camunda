let responseInterceptor = null;

export async function request({
  url,
  method,
  body,
  query,
  headers,
  skipResponseInterceptor
}) {
  const resourceUrl = query ? `${url}?${stringifyQuery(query)}` : `${url}`;

  let response = await fetch(resourceUrl, {
    method,
    credentials: 'include',
    body: typeof body === 'string' ? body : JSON.stringify(body),
    headers: {
      'Content-Type': 'application/json',
      ...headers
    },
    mode: 'cors'
  });

  if (!skipResponseInterceptor && typeof responseInterceptor === 'function') {
    await responseInterceptor(response);
  }

  if (response.status >= 200 && response.status < 300) {
    return response;
  } else {
    throw response;
  }
}

export function stringifyQuery(query) {
  return Object.keys(query).reduce((queryStr, key) => {
    const value = query[key];

    if (queryStr === '') {
      return `${key}=${encodeURIComponent(value)}`;
    }

    return `${queryStr}&${key}=${encodeURIComponent(value)}`;
  }, '');
}

export function setResponseInterceptor(fct) {
  responseInterceptor = fct;
}
