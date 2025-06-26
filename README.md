Para debugar:

  Setup de RabbitMQ:

    docker-compose down
    docker-compose build
    docker-compose up

  En otras 2 terminales ejecutar las aplicaciones respectivamente:

  Chat A:
  
    docker-compose run --rm --service-ports chatappa

  Chat B:

    docker-compose run --rm --service-ports chatappb
