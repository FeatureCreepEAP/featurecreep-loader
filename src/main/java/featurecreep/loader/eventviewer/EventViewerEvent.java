package featurecreep.loader.eventviewer;

public interface EventViewerEvent<T> {

	public String getEvent_name();

	public void setEvent_name(String event_name);

	public Object[] getParams();

	public void setParams(Object[] params);


}
