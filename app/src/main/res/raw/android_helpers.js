const BETWEEN_ACTIONS_TIMEOUT = 2000;
const by = {
  testId: (testId) => `*[data-test-id="${testId}"]`,
};

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function queryElement(testId) {
  let element = document.querySelector(by.testId(testId));
  if (!element) {
    element = document
      .getElementById('frontegg-admin-portal-container-default')
      .shadowRoot.querySelector(by.testId(testId));
  }
  if (!element) {
    element = document
      .getElementById('frontegg-login-box-container-default')
      .shadowRoot.querySelector(by.testId(testId));
  }
  return element;
}

async function findElement(testId, timeout) {
  let element = queryElement(testId);
  while (!element && timeout > 0) {
    timeout -= 100;
    await delay(100);
    element = queryElement(testId);
  }
  return element;
}

async function typeText(testId, text, timeout = 5000) {
  await delay(BETWEEN_ACTIONS_TIMEOUT);
  let element = await findElement(testId, timeout);
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  setter.call(element, text);

  const event = new Event('change', { bubbles: true });
  return element.dispatchEvent(event);
}

async function click(testId, timeout = 5000) {
  await delay(BETWEEN_ACTIONS_TIMEOUT);
  (await findElement(testId, timeout)).click();
  return true;
}
