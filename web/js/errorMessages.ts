// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

function formatErrorMessage(error) {
  const key = error[0];
  if (key === 'no-such-user') {
    return `User does not exist: ${error[1]}`;
  }
  return JSON.stringify(error);
}

export function formatApiError(error) {
  const errors = error.response?.data.errors;
  if (Array.isArray(errors)) {
    const messages = errors.map(formatErrorMessage);
    return messages.join("\n");
  }
  return error;
}
