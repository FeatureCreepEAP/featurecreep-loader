/**
 * 
 */
package featurecreep.loader.eventviewer;

import java.util.ArrayList;

/**
 * @author rhel
 *
 */
public class EventViewer {

public ArrayList<EventViewerEvent> events = new ArrayList<EventViewerEvent>();
public ArrayList<EventListener> listeners = new ArrayList<EventListener>();


public EventViewerEvent addEvent(String event_name, Object... params) {
	EventViewerEvent ev = new EventViewerEvent(event_name, params);
    events.add(ev);
    return ev;
}

public EventViewerEvent addEvent(EventViewerEvent ev) {
	events.add(ev);
	return ev;
}

public ArrayList<EventViewerEvent> getEvents(){
return events;	
}

public EventViewerEvent getEvent(String event_name) {
for(EventViewerEvent event: events) {
	if(event.getEvent_name().equals(event_name)) {
		return event;
	}
}

return null;
}

public ArrayList<EventListener> getListeners() {
	return listeners;
}

public ArrayList<EventListener> getListener(String event_name){
	ArrayList<EventListener> ret = new ArrayList<EventListener>();
	for(EventListener listener: listeners) {
		if(listener.event_name.equals(event_name)) {
			 ret.add(listener);
		}
	}

	return ret;
	
	
}


public Object[] invokeEvent(EventViewerEvent event) {
ArrayList<Object> objs = new ArrayList<Object>();
	for(EventListener listener:this.getListener(event.event_name)) {
	objs.add(listener.invoke());
}
	
	return objs.toArray();
}

public EventListener registerListener(EventListener listener) {
	listeners.add(listener);
    return listener;
}

public EventListener registerListener(String event_name, String method_name, Object instance, Class<?> ... param_classes) {
	return this.registerListener(new EventListener(this, event_name,method_name,instance,param_classes));
}


}
