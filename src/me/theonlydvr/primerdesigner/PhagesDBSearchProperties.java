package me.theonlydvr.primerdesigner;
import java.util.ArrayList;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class PhagesDBSearchProperties {
	
	public static class Property {
		public static final int EQUAL = 0;
		public static final int LOE = -1;
		public static final int LESS = -2;
		public static final int GOE = 1;
		public static final int GREATER = 2;
		
		private String[] parents;
		private String property;
		private Comparable value;
		private int comp;
		
		public Property(String[] parents, String property, Comparable value, int comp) {
			this.parents = parents;
			this.property = property;
			this.setValue(value);
			this.setComp(comp);
		}
		
		public String[] getParents() {
			return parents;
		}

		public void setParents(String[] parents) {
			this.parents = parents;
		}
		
		public String getProperty() {
			return property;
		}
		
		public void setProperty(String property) {
			this.property = property;
		}

		public Comparable getValue() {
			return value;
		}

		public void setValue(Comparable value) {
			this.value = value;
		}

		public int getComp() {
			return comp;
		}

		public void setComp(int comp) {
			this.comp = comp;
		}
	}
	
	private ArrayList<Property> properties;
	private ArrayList<String> outputs;
	
	public PhagesDBSearchProperties() {
		properties = new ArrayList<>();
		outputs = new ArrayList<>();
	}
	
	public void addOutput(String tag) {
		outputs.add(tag);
	}
	
	public void removeOutput(String tag) {
		outputs.remove(tag);
	}
	
	public void addProperty(Property property) {
		properties.add(property);
	}
	
	public void removeProperty(Property property) {
		properties.remove(property);
	}
	
	public boolean satisfies(JsonObject phage) {
		for (Property p : properties) {
			if (!phage.get(p.getProperty()).isJsonNull()) {
				Comparable value; 
				JsonObject embedded = phage;
				for (String tag : p.getParents())
					embedded = embedded.get(tag).getAsJsonObject();
				JsonPrimitive prim = embedded.get(p.getProperty()).getAsJsonPrimitive();
				if (prim.isBoolean()) value = prim.getAsBoolean();
				else if (prim.isNumber()) value = prim.getAsDouble();
				else value = prim.getAsString();
				switch (p.getComp()) {
					case Property.EQUAL: if (value.compareTo(p.getValue()) != 0) return false; break;
					case Property.LOE: if (value.compareTo(p.getValue()) > 0) return false; break;
					case Property.LESS: if (value.compareTo(p.getValue()) >= 0) return false; break;
					case Property.GOE: if (value.compareTo(p.getValue()) < 0) return false; break;
					case Property.GREATER: if (value.compareTo(p.getValue()) <= 0) return false; break;
				}
			} 
			else return false;
		}
		return true;
	}
	
	public JsonObject filter(JsonObject phage) {
		if (outputs.size() > 0) {
			JsonObject filtered = new JsonObject();
			for (String tag : outputs) {
				if (!phage.get(tag).isJsonNull())
					filtered.add(tag, phage.get(tag));
			}
			return filtered;
		}
		return phage;
	}
}