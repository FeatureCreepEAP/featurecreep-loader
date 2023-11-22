package featurecreep.loader.eventviewer;

public class EventViewerEvent {

public String event_name;
public EventViewerEvent(String event_name, Object... params) {
	super();
	this.event_name = event_name;
	this.params = params;
}
public String getEvent_name() {
	return event_name;
}
public void setEvent_name(String event_name) {
	this.event_name = event_name;
}
public Object[] getParams() {
	return params;
}
public void setParams(Object[] params) {
	this.params = params;
}
public Object[] params;
	
	

}
