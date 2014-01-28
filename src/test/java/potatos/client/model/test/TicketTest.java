package potatos.client.model.test;

import java.io.File;
import java.io.IOException;

import potatos.client.model.Ticket;
import potatos.client.model.TicketItem;
import potatos.client.model.TicketItemProperty;

import com.google.gson.*;
import com.google.common.base.Charsets;
import com.google.common.io.*;
public class TicketTest {
	static String processReceipt(Ticket rd, Integer id) throws Exception {
		String r = "";
		int width = rd.getLineWidth()==null?40:rd.getLineWidth();
		if(rd.getMode()==null||rd.getMode().equals("receipt")){
			r += "<center><h4>"+rd.getTitle()+"</h4></center>\n\n";
			r += "Items:\n";
			for(TicketItem ti:rd.getTicketItems()){
				r+="------------------\n";
				r+=String.format("<h3>#%d %s%s</h3>\n<right>%d * $%.2f</right>\n",
						ti.getId(),
						ti.getName(),
						ti.getPortion()==null?"":"."+ti.getPortion(),
						ti.getQuantity(),
						ti.getPrice());
				for(TicketItemProperty tip:ti.getProperties()){
					r+=String.format("-- %s\n<right>%d * $%.2f</right>\n.", tip.getName(),tip.getQuantity(),tip.getPrice());
				}
			}
			r+="------------------\n";
			r+=String.format("Subtotal: \n<right>%f</right>\n",rd.getSubtotal());
			if(rd.getTax()!=null)r+=String.format("Tax: \n<right>%f</right>\n",rd.getTax());
			if(rd.getServiceCharge()!=null&&rd.getServiceCharge()>0)r+=String.format("Service: \n<right>%f</right>\n",rd.getServiceCharge());
			if(rd.getDiscount()!=null&&rd.getDiscount()>0)r+=String.format("Discount: \n<right>%f</right>\n", rd.getDiscount());
			r+=String.format("Total: \n<right>%f</right>\n",rd.getTotal());
			
			if(rd.getType()=="online"){
				r += "Name: " + rd.getName() + "\n";
				r += "Phone: " + rd.getPhone() + "\n";
				r += "Email: " + rd.getEmail() + "\n";
				r += "Comments: <h3>" + rd.getComments() + "</h3>\n";
			}
			if(rd.getBarcode()!=null){
				r += "Barcode: "+rd.getBarcode()+"\n<barcode>"+rd.getBarcode()+"</barcode>\n";
			}
			//r=parser.convert(r);
			//r=filterToPrintableString(r,width);
		}else if(rd.getMode().equals("tagged")){
			//r += parser.convert(rd.getComments());
		}
		r += "\n\nPowered By PrintOS http://printos.io/\n";
		r += "Cloud printing & receipt ad solution\n\n";
		r += "Designed By PotatOS http://potatos.cc/\n";
		r += "A leading cloud restaurant system provider";
		r +="\n\n\n\n\n\n";
		//System.out.println(r);
		return r;
	}
	public static void main(String[] args) {
		Gson gson = new Gson();
		String str=null;
		try {
			str = Files.toString(new File("jsontest"), Charsets.US_ASCII);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Ticket rd = gson.fromJson(str, Ticket.class);
		try {
			System.out.println(processReceipt(rd,1));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
