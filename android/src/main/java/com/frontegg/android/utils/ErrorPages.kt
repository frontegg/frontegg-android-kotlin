package com.frontegg.android.utils


fun generateErrorPage(
    message: String,
    status: Int? = null,
    error: String? = null,
    url: String? = null
): String {
    return """
        <html lang="en"><head>
  <meta charset="UTF-8">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  
  <style>
    a,
    button,
    input,
    select,
    h1,
    h2,
    h3,
    h4,
    h5,
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      border: none;
      text-decoration: none;
      appearance: none;
      background: none;
      -webkit-font-smoothing: antialiased;
    }
    
    /* Figma Styles of your File */
:root {
  /* Colors */
  --default-ap-secondary-hover: linear-gradient(
    180deg,
    rgba(251, 251, 252, 1) 0%,
    rgba(240, 243, 248, 1) 100%
  );
  --default-ap-secondary-active: linear-gradient(
    180deg,
    rgba(247, 247, 250, 1) 0%,
    rgba(230, 236, 244, 1) 100%
  );
  --default-ap-white: #ffffff;
  --default-ap-grey-100: #fbfbfc;
  --default-ap-grey-150: #f5f6f8;
  --default-ap-grey-200: #e5eaf3;
  --default-ap-grey-400: #d1dbea;
  --default-ap-grey-500: #adbcd2;
  --default-ap-grey-600: #8b9db8;
  --default-ap-grey-700: #717f94;
  --default-ap-grey-800: #3c4a5a;
  --default-ap-grey-900: #1d2630;
  --default-ap-primary: #43bb7a;
  --default-ap-darker-primary: #36a76a;
  --default-ap-primary-light: #c6f0d9;
  --default-ap-darker-primary-disable: #72cc9b;
  --default-ap-light-danger: #ed8e7c;
  --default-ap-danger: #e1583e;
  --default-ap-darker-danger: #bb041c;
  --default-ap-danger-hover: linear-gradient(
    180deg,
    rgba(225, 88, 62, 1) 0%,
    rgba(197, 62, 37, 1) 100%
  );
  --default-ap-danger-active: linear-gradient(
    180deg,
    rgba(215, 82, 56, 1) 0%,
    rgba(172, 46, 22, 1) 100%
  );
  --default-ap-main-toggle: #03a9f4;
  --default-ap-toggle-bg: #9ae0ff;
  --default-ap-toggle-hover: #0092d3;
  --default-ap-toggle-bg-hover: #78c9ee;
  --default-ap-cyan-blue-hover: #d1e3f4;
  --default-ap-light-info: #e2eef9;
  --default-ap-dark-info: #b1cae7;
  --default-ap-main-info: #5587c0;
  --default-ap-light-success: #e1f5e2;
  --default-ap-dark-success: #a2e1bf;
  --default-ap-main-success: #2ca744;
  --default-ap-light-error: #ffeeea;
  --default-ap-dark-error: #f1d1ca;
  --default-ap-main-error: #e1583e;
  --default-ap-light-warning: #f9f4e2;
  --default-ap-dark-warning: #eae1c2;
  --default-ap-main-warning: #a79d7b;
  --default-ap-graph-1: #ffd5cc;
  --default-ap-graph-2: #ffa08b;
  --default-ap-primary-hover: linear-gradient(
    180deg,
    rgba(67, 187, 122, 1) 0%,
    rgba(50, 161, 101, 1) 100%
  );
  --default-ap-primary-active: linear-gradient(
    180deg,
    rgba(61, 176, 114, 1) 0%,
    rgba(39, 136, 84, 1) 100%
  );
  --modern-grey-800: #2f334b;
  --modern-grey-700: #565a73;
  --modern-grey-600: #9699af;
  --modern-grey-400: #d0d2e1;
  --modern-grey-300: #e6e6f0;
  --modern-grey-200: #f1f1f8;
  --modern-grey-100: #fcfcff;
  --modern-white: #ffffff;
  --modern-primary: #7075e8;
  --modern-darker-primary: #6267e2;
  --modern-primary-light: #d4d5fe;
  --modern-danger: #de6161;
  --modern-darker-danger: #c75151;
  --modern-light-danger: #efb8b8;
  --modern-danger-hover: #bc4340;
  --modern-main-success: #3aa652;
  --modern-light-success: #eafcee;
  --modern-main-error: #ea5c5c;
  --modern-light-error: #fff2f2;
  --modern-main-warning: #d99f30;
  --modern-light-warning: #fff8ea;
  --modern-main-info: #5792f5;
  --modern-light-info: #eff5ff;
  --modern-primary-light-toggle: #e5e7f9;
  --vivid-grey-800: #36384b;
  --vivid-grey-700: #8489a7;
  --vivid-grey-600: #9fa3bc;
  --vivid-grey-400: #cdcfe0;
  --vivid-grey-200: #ecedf8;
  --vivid-white: #ffffff;
  --vivid-white-95percent: rgba(255, 255, 255, 0.95);
  --vivid-darker-primary: #2b48ed;
  --vivid-primary: #3956fb;
  --vivid-primary-light: #d3daff;
  --vivid-danger: #de6161;
  --vivid-darker-danger: #c75151;
  --vivid-light-danger: #efb8b8;
  --vivid-main-info: #2b7fff;
  --vivid-light-info: #d3e5ff;
  --vivid-main-warning: #ffa216;
  --vivid-light-warning: #ffedd1;
  --vivid-main-success: #4cc056;
  --vivid-light-success: #ddf6df;
  --vivid-main-error: #eb3e3e;
  --vivid-light-error: #fed6d6;
  --dark-black: #0a0b0c;
  --dark-grey-800: rgba(255, 255, 255, 0.08);
  --dark-grey-700: rgba(255, 255, 255, 0.24);
  --dark-grey-600: rgba(255, 255, 255, 0.4);
  --dark-grey-400: rgba(255, 255, 255, 0.64);
  --dark-grey-200: rgba(255, 255, 255, 0.8);
  --dark-white: #ffffff;
  --dark-primary-active: #3e9d6a;
  --dark-darker-primary: #0b582f;
  --dark-primary: #4ba976;
  --dark-primary-light: #98cdb0;
  --dark-danger: #de6161;
  --dark-danger-hover: #bc4340;
  --dark-darker-danger: #c75151;
  --dark-light-danger: #efb8b8;
  --dark-main-success: #309e54;
  --dark-dark-success: #083417;
  --dark-main-error: #bb5947;
  --dark-dark-error: #3d0909;
  --dark-main-warning: #a58e41;
  --dark-dark-warning: #44370b;
  --dark-main-info: #5186ba;
  --dark-dark-info: #0a2540;
  --classic-grey-800: #373739;
  --classic-grey-700: #7a7c81;
  --classic-grey-400: #c3c5ca;
  --classic-grey-200: #e6e8ec;
  --classic-grey-100: #f6f8fc;
  --classic-grey-50: #ebf0fe;
  --classic-white: #ffffff;
  --classic-darker-primary: #3a6ae5;
  --classic-primary: #4d7dfa;
  --classic-primary-light: #cad7f9;
  --classic-danger: #de6161;
  --classic-danger-hover: #bc4340;
  --classic-darker-danger: #c75151;
  --classic-light-danger: #efb8b8;
  --classic-main-info: #498aeb;
  --classic-light-info: #ebf3ff;
  --classic-main-success: #4da82d;
  --classic-light-success: #e8fee0;
  --classic-main-error: #ea5c5c;
  --classic-light-error: #fff4f4;
  --classic-main-warning: #f0a534;
  --classic-light-warning: #fff7ec;

  /* Fonts */
  --aph1-font-family: Manrope-Bold, sans-serif;
  --aph1-font-size: 30px;
  --aph1-line-height: normal;
  --aph1-font-weight: 700;
  --aph1-font-style: normal;
  --aph2-font-family: Manrope-SemiBold, sans-serif;
  --aph2-font-size: 26px;
  --aph2-line-height: normal;
  --aph2-font-weight: 600;
  --aph2-font-style: normal;
  --aph3-font-family: Manrope-ExtraBold, sans-serif;
  --aph3-font-size: 18px;
  --aph3-line-height: normal;
  --aph3-font-weight: 800;
  --aph3-font-style: normal;
  --apsubtitle-1-font-family: Manrope-SemiBold, sans-serif;
  --apsubtitle-1-font-size: 16px;
  --apsubtitle-1-line-height: normal;
  --apsubtitle-1-font-weight: 600;
  --apsubtitle-1-font-style: normal;
  --apsubtitle2-font-family: Manrope-SemiBold, sans-serif;
  --apsubtitle2-font-size: 14px;
  --apsubtitle2-line-height: normal;
  --apsubtitle2-font-weight: 600;
  --apsubtitle2-font-style: normal;
  --apsubtitle3-font-family: Manrope-SemiBold, sans-serif;
  --apsubtitle3-font-size: 12px;
  --apsubtitle3-line-height: normal;
  --apsubtitle3-font-weight: 600;
  --apsubtitle3-font-style: normal;
  --apbody1-font-family: Manrope-Regular, sans-serif;
  --apbody1-font-size: 16px;
  --apbody1-line-height: normal;
  --apbody1-font-weight: 400;
  --apbody1-font-style: normal;
  --apbody2-font-family: Manrope-Regular, sans-serif;
  --apbody2-font-size: 14px;
  --apbody2-line-height: normal;
  --apbody2-font-weight: 400;
  --apbody2-font-style: normal;
  --apbody3-font-family: Manrope-Regular, sans-serif;
  --apbody3-font-size: 12px;
  --apbody3-line-height: normal;
  --apbody3-font-weight: 400;
  --apbody3-font-style: normal;
  --apoverline1-font-family: Manrope-Bold, sans-serif;
  --apoverline1-font-size: 12px;
  --apoverline1-line-height: normal;
  --apoverline1-font-weight: 700;
  --apoverline1-font-style: normal;
  --apoverline2-font-family: Manrope-Bold, sans-serif;
  --apoverline2-font-size: 10px;
  --apoverline2-line-height: normal;
  --apoverline2-font-weight: 700;
  --apoverline2-font-style: normal;

  /* Effects */
  --modern-cards-shadow-box-shadow: 0px 4px 16px 0px rgba(171, 175, 201, 0.12);
  --modern-modal-shadow-box-shadow: 0px 4px 24px 0px rgba(47, 51, 75, 0.08);
  --modern-dropdown-shadow-box-shadow: 0px 4px 8px 0px rgba(171, 175, 201, 0.24);
  --modern-menu-shadow-box-shadow: 0px 4px 8px 0px rgba(171, 175, 201, 0.24);
  --modern-buttons-primary-shadow-box-shadow: 0px 8px 24px 0px
    rgba(112, 117, 232, 0.24);
  --modern-buttons-secondary-shadow-box-shadow: 0px 4px 8px 0px
    rgba(171, 175, 201, 0.24);
  --modern-tabs-hover-shadow-box-shadow: 0px 4px 16px 0px
    rgba(112, 117, 232, 0.16);
  --modern-input-hover-shadow-box-shadow: 0px 4px 4px 0px
    rgba(112, 117, 232, 0.08);
  --classic-modal-shadow-box-shadow: 0px 4px 24px 0px rgba(47, 51, 75, 0.08);
  --classic-card-shadow-box-shadow: 0px 4px 16px 0px rgba(171, 175, 201, 0.12);
  --classic-dropdown-shadow-box-shadow: 0px 2px 4px 0px
    rgba(122, 124, 129, 0.24);
  --classic-menu-shadow-box-shadow: 0px 2px 4px 0px rgba(122, 124, 129, 0.24);
  --vivid-modal-shadow-box-shadow: 0px 4px 24px 0px rgba(47, 51, 75, 0.08);
  --vivid-dropdown-shadow-box-shadow: 0px 8px 16px 0px rgba(54, 56, 75, 0.08);
  --vivid-menu-shadow-box-shadow: 0px 8px 16px 0px rgba(54, 56, 75, 0.08);
  --dark-modal-shadow-box-shadow: 0px 4px 200px 0px rgba(10, 11, 12, 1);
  --default-ap-modal-shadow-box-shadow: 0px 16px 24px 0px rgba(34, 49, 74, 0.1);
  --default-ap-menu-shadow-box-shadow: 0px 4px 12px 0px rgba(10, 27, 62, 0.11);
  --default-ap-card-shadow-box-shadow: 0px 1px 2px 0px rgba(216, 219, 224, 1);
  --frontegg-menu-shadow-box-shadow: 0px 4px 8px 0px rgba(171, 175, 201, 0.24);
}

html {
  height: 100%;
}

body {
  height: 100%;
}


.content,
.content * {
  box-sizing: border-box;
}

.content {
  height: 100%;
  background: var(--white, #ffffff);
  border-radius: 8px;
  border-style: solid;
  border-color: var(--grey-200, #e4e4e4);
  border-width: 1px;
  padding: 48px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  align-items: center;
  justify-content: center;
  align-self: stretch;
  flex: 1;
  position: relative;
  overflow: hidden;
}

.frame-1000005862 {
  background: var(--grey-50, #f9f9f9);
  border-radius: 8px;
  padding: 4px;
  display: flex;
  flex-direction: row;
  gap: 8px;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 80px;
  height: 80px;
  position: relative;
}

.mood-bad {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  position: relative;
  overflow: visible;
}

.dashboard-section-header {
  padding: 0px 0px 16px 0px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  align-items: center;
  justify-content: flex-start;
  flex-shrink: 0;
}

.texts {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: flex-start;
}

.title {
  color: var(--grey-800, #323232);
  text-align: left;
  font-family: var(--h1-font-family, "Manrope-SemiBold", sans-serif);
  font-size: var(--h1-font-size, 32px);
  letter-spacing: var(--h1-letter-spacing, 0.01em);
  font-weight: var(--h1-font-weight, 600);
  position: relative;
}

.texts2 {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: flex-start;
}

.title2 {
  color: var(--grey-800, #323232);
  text-align: left;
  font-family: var(--h4-font-family, "Manrope-Medium", sans-serif);
  font-size: var(--h4-font-size, 18px);
  letter-spacing: var(--h4-letter-spacing, 0.01em);
  font-weight: var(--h4-font-weight, 500);
  position: relative;
}

.actions {
  display: flex;
  flex-direction: row;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
  position: relative;
}

.dashboard-button {
  background: var(--primary-blue-500, #3d65f0);
  border-radius: 4px;
  padding: 4px 16px 4px 16px;
  display: flex;
  flex-direction: row;
  gap: 10px;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

.label {
  color: var(--white, #ffffff);
  text-align: left;
  font-family: var(--subtitle-1-font-family, "Manrope-Bold", sans-serif);
  font-size: var(--subtitle-1-font-size, 14px);
  line-height: var(--subtitle-1-line-height, 24px);
  letter-spacing: var(--subtitle-1-letter-spacing, 0.01em);
  font-weight: var(--subtitle-1-font-weight, 700);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  cursor: pointer;
}
  </style>
  
</head>

<body>

<script type="text/javascript">
    function retry(){
      window.location.replace("${url?.replace("\"", "\\\"")}");
    }
    
    setTimeout(function() {
      retry();
    }, 4000);
</script>
  <div class="content">
    <div class="frame-1000005862">
      <img class="mood-bad" src="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDEiIHZpZXdCb3g9IjAgMCA0MCA0MSIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGcgY2xpcC1wYXRoPSJ1cmwoI2NsaXAwXzMxMTRfMjU3OCkiPgo8cGF0aCBkPSJNMTkuOTgzMyAzLjgzMzM0QzEwLjc4MzMgMy44MzMzNCAzLjMzMzMzIDExLjMgMy4zMzMzMyAyMC41QzMuMzMzMzMgMjkuNyAxMC43ODMzIDM3LjE2NjcgMTkuOTgzMyAzNy4xNjY3QzI5LjIgMzcuMTY2NyAzNi42NjY3IDI5LjcgMzYuNjY2NyAyMC41QzM2LjY2NjcgMTEuMyAyOS4yIDMuODMzMzQgMTkuOTgzMyAzLjgzMzM0Wk0yMCAzMy44MzMzQzEyLjYzMzMgMzMuODMzMyA2LjY2NjY3IDI3Ljg2NjcgNi42NjY2NyAyMC41QzYuNjY2NjcgMTMuMTMzMyAxMi42MzMzIDcuMTY2NjcgMjAgNy4xNjY2N0MyNy4zNjY3IDcuMTY2NjcgMzMuMzMzMyAxMy4xMzMzIDMzLjMzMzMgMjAuNUMzMy4zMzMzIDI3Ljg2NjcgMjcuMzY2NyAzMy44MzMzIDIwIDMzLjgzMzNaTTI1LjgzMzMgMTguODMzM0MyNy4yMTY3IDE4LjgzMzMgMjguMzMzMyAxNy43MTY3IDI4LjMzMzMgMTYuMzMzM0MyOC4zMzMzIDE0Ljk1IDI3LjIxNjcgMTMuODMzMyAyNS44MzMzIDEzLjgzMzNDMjQuNDUgMTMuODMzMyAyMy4zMzMzIDE0Ljk1IDIzLjMzMzMgMTYuMzMzM0MyMy4zMzMzIDE3LjcxNjcgMjQuNDUgMTguODMzMyAyNS44MzMzIDE4LjgzMzNaTTE0LjE2NjcgMTguODMzM0MxNS41NSAxOC44MzMzIDE2LjY2NjcgMTcuNzE2NyAxNi42NjY3IDE2LjMzMzNDMTYuNjY2NyAxNC45NSAxNS41NSAxMy44MzMzIDE0LjE2NjcgMTMuODMzM0MxMi43ODMzIDEzLjgzMzMgMTEuNjY2NyAxNC45NSAxMS42NjY3IDE2LjMzMzNDMTEuNjY2NyAxNy43MTY3IDEyLjc4MzMgMTguODMzMyAxNC4xNjY3IDE4LjgzMzNaTTIwIDIzQzE2LjExNjcgMjMgMTIuODE2NyAyNS40MzMzIDExLjQ4MzMgMjguODMzM0gyOC41MTY3QzI3LjE4MzMgMjUuNDMzMyAyMy44ODMzIDIzIDIwIDIzWiIgZmlsbD0iI0FCQUJBQiIvPgo8L2c+CjxkZWZzPgo8Y2xpcFBhdGggaWQ9ImNsaXAwXzMxMTRfMjU3OCI+CjxyZWN0IHdpZHRoPSI0MCIgaGVpZ2h0PSI0MCIgZmlsbD0id2hpdGUiIHRyYW5zZm9ybT0idHJhbnNsYXRlKDAgMC41KSIvPgo8L2NsaXBQYXRoPgo8L2RlZnM+Cjwvc3ZnPgo=" crossorigin="anonymous">
    </div>
    <div class="dashboard-section-header">
      <div class="group-1000005862">
        <div class="texts">
          <div class="title">Uh-oh!</div>
        </div>
        <div class="texts2">
          <div class="title2">${message}</div>
        </div>
      </div>
      <div class="actions">
        <div class="dashboard-button">
          <div onclick="retry()" class="label">Try again</div>
        </div>
      </div>
    </div>
  </div>

</body>

</html>
    """
}
