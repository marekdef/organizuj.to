package pl.mobilization.organizuj.to.json;

import com.google.gson.annotations.SerializedName;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * Created by marekdef on 12.10.14.
 */
public class Guest {
    @SerializedName(value = "guest_id")
    public String id;
    public boolean is_present;

    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this).toString();
    }
}
