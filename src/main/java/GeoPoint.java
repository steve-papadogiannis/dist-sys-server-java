import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public final class GeoPoint implements Serializable {

    private double latitude, longitude;

    GeoPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    GeoPoint() {}

    @JsonProperty("latitude")
    double getLatitude() {
        return latitude;
    }

    @JsonProperty("longitude")
    double getLongitude() {
        return longitude;
    }

    @JsonProperty("latitude")
    void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    @JsonProperty("longitude")
    void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    double euclideanDistance(GeoPoint other) {
        return Math.sqrt((Math.pow((latitude - other.latitude), 2) + Math.pow((longitude - other.longitude), 2)));
    }

    @Override
    public String toString() {
        return "GeoPoint [ Latitude = " + latitude + ", Longitude = " + longitude + "]";
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 11 * hash + Double.valueOf(latitude).hashCode();
        hash = 11 * hash + Double.valueOf(longitude).hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (!(obj instanceof GeoPoint)) {
            return false;
        }

        final GeoPoint other = (GeoPoint) obj;

        return Double.doubleToLongBits(latitude) == Double.doubleToLongBits(other.latitude) &&
               Double.doubleToLongBits(longitude) == Double.doubleToLongBits(other.longitude);
    }

}
