package featurecreep.loader.eventviewer.events;

import featurecreep.loader.eventviewer.EventViewerEvent;

public class BasicEvent<T> implements EventViewerEvent<T> {

	public String event_name;
	public Object[] params;

	public BasicEvent(String event_name, Object... params) {
		this.event_name = event_name;
		this.params = params;
	}

	@Override
	public String getEvent_name() {
		return event_name;
	}

	@Override
	public void setEvent_name(String event_name) {
		this.event_name = event_name;
	}

	@Override
	public Object[] getParams() {
		return params;
	}

	@Override
	public void setParams(Object[] params) {
		this.params = params;
	}

}
