# Data Transfer plane

Endpoints, where consumer connector can pull data from, should be in form of:

 - *https://provider.connector/data/{consumer.connector.id}/{artifact.id}*
 - *https://provider.connector/data/{consumer.connector.id}/{agreement.id}/{artifact.id}*

Once agreement is made, event should be broadcasted and listeners needs to make changes to update data accordingly.

Same logic should be applied when *TransferRequestMessage* - *TransferStartMessage* are exchanged. 

Event examples:

TransferStartMessage Success

```
{
  consumer: id
  agreement: id
  artifact: id
  status: available
  dateTimeStamp
  ...
}
```

Event should update data, so that when consumer connector tries to hit endpoint, based on the TrasnferMessage result, data will be available on that endpoint or not. Like simple check if status field is available for consumer, agreement and artifact, if it is available, consumer will download data, otherwise, it will get 503.

## Event examples

Publisher:

Where we need to publish event we can include:

```
@Autowired
private ApplicationEventPublisher applicationEventPublisher;
```

and where we need to publish event, we can call:

```
applicationEventPublisher.publishEvent(anyObject);
```
Listener example:

```
@Component
public class CatalogEventListener {

	@EventListener
	public void handleContextStart(AnyObject anyObject) {
		System.out.println("Handling context started event. " + anyObject);
	}
}

```
