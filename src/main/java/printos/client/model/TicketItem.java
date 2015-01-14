package printos.client.model;

public class TicketItem {
	Integer id;
	String name;
	String portion;
	Integer quantity;
	Double price;
	TicketItemProperty[] properties;
	public Integer getId() {
		return id;
	}
	public void setId(Integer id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getPortion() {
		return portion;
	}
	public void setPortion(String portion) {
		this.portion = portion;
	}
	public Integer getQuantity() {
		return quantity;
	}
	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}
	public Double getPrice() {
		return price;
	}
	public void setPrice(Double price) {
		this.price = price;
	}
	public TicketItemProperty[] getProperties() {
		return properties;
	}
	public void setProperties(TicketItemProperty[] properties) {
		this.properties = properties;
	}
	
}
