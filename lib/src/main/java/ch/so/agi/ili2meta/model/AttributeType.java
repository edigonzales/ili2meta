package ch.so.agi.ili2meta.model;

public class AttributeType {
    private String name;
    private String title;
    private String description;
    private DataType dataType;
    private boolean mandatory;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public DataType getDataType() {
        return dataType;
    }
    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }
    public boolean isMandatory() {
        return mandatory;
    }
    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }
    @Override
    public String toString() {
        return "AttributeType [name=" + name + ", title=" + title + ", description=" + description + ", dataType="
                + dataType + ", mandatory=" + mandatory + "]";
    }
}
