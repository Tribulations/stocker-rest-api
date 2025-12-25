# Stocker REST API
A REST API built in Spring Boot for accessing stock market data.

The stock data is retrieved from a PostgreSQL database which is populated by the [Stocker Data Collector](https://github.com/Tribulations/stocker-data-collector) component.

- API documentation is available via **Swagger UI / OpenAPI**. http://localhost:8080/swagger-ui/index.html
- Modified the nineones.xyz-le-ssl something apache2 conf file to listen on port /api port 8080 on the vardagsrum server comp.
- Deployed a version of this app on server in vardagsrum and added a systemd service named stocker-rest-api.service
