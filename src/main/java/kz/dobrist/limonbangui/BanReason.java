package kz.dobrist.limonbangui;

public record BanReason(String key, String label, int days, boolean permanent, boolean dramatic) {
}
