# swagger-mock-server
Swagger mock server based on akka-http 

Experimental project for auto-generating mock-server from Swagger spec 

* using akka-http static build the route from the swagger file .. 
* using [yod-mock](https://github.com/qiu8310/yod-mock) to generate mock data

## Feature 

* add `x-yod-type` on definitions can specify the generate behavior

```
 properties:
      id:
        type: integer
        format: int32
        description: key
        x-yod-type: '@Int(50, 100)'

```

or can aware of the type-name from the response schema which is defined by `x-yod-name`

```
 responses:
   '200':
    description: real bank
    schema:
      type: array
      x-yod-name: bank
      items:
      $ref: '#/definitions/GeneralItem'
      
...


 GeneralItem:
    description: Tuple[integer, String]
    type: object
    properties:
      id:
        type: integer
        format: int32
        description: key
      name:
        type: string
        description: name
        x-yod-type:
          bank: '@(["中国银行", "工商银行", "农业银行", "建设银行", "交通银行"]).sample @([沪太路支行, 五角场支行]).sample'
```


### support size&page context

context var  is a string start with '$'

* size: the request size 
* page: the request page
* max: max of the mock data

```
 x-yod-array:
    size: '$size'
    page: '$page'
    max: 100
```
### support chance to return a response definitions object

once you define a reponse definiitons object with following extentions 
```
responses:
  GeneralError:
    description: Entity not found
    schema:
      $ref: "#/definitions/Response"
    x-is-global: true
    x-chance: 0.2
    x-status-code: 503
  GeneralResponse:
    description: General Response
    schema:
      $ref: "#/definitions/Response"

```

* `x-is-global` will make other extentions to work  accept true/false
* `x-chance` a chance that response will take place if multiply responsese have been defined take the first in order 
* `x-status-code` bind the response object to a fixed HttpStatusCode


### How to Start

* prepare your swagger spec file xxx.yml
* build this project

```
mvn clean install
```
* run with java
 
```
java -jar mock-server-1.0-snapshot.jar path-to-file port
```


## TODO

* Bind Context to the Response to build context-based Response
* make the refactor ..lame code
* pass the Swagger test (Done with my own yaml file)

 
