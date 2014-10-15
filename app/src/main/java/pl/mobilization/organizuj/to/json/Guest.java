package pl.mobilization.organizuj.to.json;

import com.google.gson.annotations.SerializedName;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * Created by marekdef on 12.10.14.
 */
public class Guest {
    public Guest() {

    }
    @SerializedName(value = "guest_id")
    public int id;
    public boolean is_present;

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Guest guest = (Guest) o;

        if (id != guest.id) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
