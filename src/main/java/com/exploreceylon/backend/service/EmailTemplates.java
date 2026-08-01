package com.exploreceylon.backend.service;

/**
 * Builds the branded HTML used for verification-code emails. Plain,
 * table-based markup with inline styles only — the layout Gmail and Outlook
 * both render consistently (no external CSS, no flexbox/grid).
 */
final class EmailTemplates {

    private static final String BRAND_GREEN = "#2D6A4F";

    private EmailTemplates() {
    }

    static String verificationCodeHtml(String recipientName, String code, int expiryMinutes) {
        String greetingName = (recipientName == null || recipientName.isBlank()) ? "there" : recipientName;
        return "<!doctype html>"
                + "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f4f6f5;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f6f5;padding:24px 0;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:480px;width:100%;background-color:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;\">"
                + "<tr><td style=\"background-color:" + BRAND_GREEN + ";padding:24px 32px;\">"
                + "<span style=\"font-size:20px;font-weight:bold;color:#ffffff;letter-spacing:0.3px;\">&#128205; Explore Ceylon</span>"
                + "</td></tr>"
                + "<tr><td style=\"padding:32px;\">"
                + "<p style=\"margin:0 0 16px 0;font-size:16px;color:#111827;\">Hi " + escape(greetingName) + ",</p>"
                + "<p style=\"margin:0 0 24px 0;font-size:14px;line-height:1.6;color:#374151;\">"
                + "Use the verification code below to confirm it's you. Enter it in the app to continue."
                + "</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
                + "<tr><td align=\"center\" style=\"padding:8px 0 24px 0;\">"
                + "<span style=\"display:inline-block;font-size:32px;font-weight:bold;letter-spacing:8px;color:" + BRAND_GREEN + ";"
                + "background-color:#f0f7f4;border:1px solid #d1e7dd;border-radius:8px;padding:14px 28px;\">"
                + escape(code) + "</span>"
                + "</td></tr></table>"
                + "<p style=\"margin:0 0 16px 0;font-size:13px;color:#6b7280;\">This code expires in "
                + expiryMinutes + " minutes.</p>"
                + "<p style=\"margin:0;font-size:13px;color:#6b7280;\">"
                + "If you didn't request this code, you can safely ignore this email — no changes will be made to your account."
                + "</p>"
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 32px;background-color:#f9fafb;border-top:1px solid #e5e7eb;\">"
                + "<p style=\"margin:0;font-size:12px;color:#9ca3af;\">&copy; Explore Ceylon &mdash; Discover Sri Lanka.</p>"
                + "</td></tr>"
                + "</table>"
                + "</td></tr></table>"
                + "</body></html>";
    }

    static String verificationCodePlainText(String recipientName, String code, int expiryMinutes) {
        String greetingName = (recipientName == null || recipientName.isBlank()) ? "there" : recipientName;
        return "Hi " + greetingName + ",\n\n"
                + "Your Explore Ceylon verification code is: " + code + "\n\n"
                + "This code expires in " + expiryMinutes + " minutes.\n\n"
                + "If you didn't request this code, you can safely ignore this email.\n\n"
                + "Explore Ceylon";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
