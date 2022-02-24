// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

const { createProxyMiddleware } = require("http-proxy-middleware");

/**
 * Setup of proxy for Webpack Dev Server to workaround CORS issues in development mode
 * @param app
 */
module.exports = function proxyConfiguration(app) {
  app.use(
    "/api/dashboards",
    createProxyMiddleware({
      // NOTE: in order to access local BackEnd installation
      // specify API_BASE_URL environment variable equal 'http://localhost:8080/api',
      // for example in .env file
      target: "http://localhost:8081/api" || "https://dev-dfp-mr.azurefd.net",
      secure: false,
      changeOrigin: true,
      logLevel: "debug",
      pathRewrite: {
        "^/api/dashboards": "dashboards",
      },
    })
  );
  app.use(
    "/api/collected-info",
    createProxyMiddleware({
      // NOTE: in order to access local BackEnd installation
      // specify API_BASE_URL environment variable equal 'http://localhost:8080/api',
      // for example in .env file
      target: "http://localhost:8081/api" || "https://dev-dfp-mr.azurefd.net",
      secure: false,
      changeOrigin: true,
      logLevel: "debug",
      pathRewrite: {
        "^/api/collected-info": "collected-info",
      },
    })
  );
  app.use(
    "/api",
    createProxyMiddleware({
      // NOTE: in order to access local BackEnd installation
      // specify API_BASE_URL environment variable equal 'http://localhost:8080/api',
      // for example in .env file
      target: "http://localhost:8080/api" || "https://dev-dfp-mr.azurefd.net",
      secure: false,
      changeOrigin: true,
      logLevel: "debug",
      pathRewrite: {
        "^/api": "",
      },
    })
  );
};
