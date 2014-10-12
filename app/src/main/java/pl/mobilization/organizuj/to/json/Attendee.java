package pl.mobilization.organizuj.to.json;

import com.google.gson.annotations.SerializedName;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * Created by marekdef on 08.10.14.
 */
public class Attendee {
    public long id;
    public boolean is_present;
    public String email;
    public String first_name;
    public String last_name;
    @SerializedName(value = "guest_type_description")
    public String type;
    public String url;

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Attendee attendee = (Attendee) o;

        if (id != attendee.id) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
