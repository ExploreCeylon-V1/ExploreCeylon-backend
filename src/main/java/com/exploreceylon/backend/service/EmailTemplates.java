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

    static String kycApprovedHtml(String recipientName) {
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
                + "<div style=\"text-align:center;margin-bottom:20px;\">"
                + "<span style=\"display:inline-block;font-size:40px;\">&#9989;</span>"
                + "<h2 style=\"margin:8px 0 0 0;color:#111827;font-size:20px;\">Identity Verified!</h2>"
                + "</div>"
                + "<p style=\"margin:0 0 16px 0;font-size:16px;color:#111827;\">Hi " + escape(greetingName) + ",</p>"
                + "<p style=\"margin:0 0 16px 0;font-size:14px;line-height:1.6;color:#374151;\">"
                + "Great news! Your identity document has been reviewed and <strong>approved</strong> by our team."
                + "</p>"
                + "<p style=\"margin:0 0 24px 0;font-size:14px;line-height:1.6;color:#374151;\">"
                + "You can now book certified Tour Guides and local Vehicles seamlessly across Sri Lanka."
                + "</p>"
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 32px;background-color:#f9fafb;border-top:1px solid #e5e7eb;\">"
                + "<p style=\"margin:0;font-size:12px;color:#9ca3af;\">&copy; Explore Ceylon &mdash; Discover Sri Lanka.</p>"
                + "</td></tr>"
                + "</table>"
                + "</td></tr></table>"
                + "</body></html>";
    }

    static String kycApprovedPlainText(String recipientName) {
        String greetingName = (recipientName == null || recipientName.isBlank()) ? "there" : recipientName;
        return "Hi " + greetingName + ",\n\n"
                + "Great news! Your identity document has been reviewed and APPROVED by the Explore Ceylon team.\n\n"
                + "You can now book Tour Guides and Vehicles.\n\n"
                + "Explore Ceylon";
    }

    static String kycRejectedHtml(String recipientName, String reason) {
        String greetingName = (recipientName == null || recipientName.isBlank()) ? "there" : recipientName;
        String rejectionReason = (reason == null || reason.isBlank()) ? "Document was unreadable or invalid." : reason;
        return "<!doctype html>"
                + "<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f4f6f5;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f6f5;padding:24px 0;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:480px;width:100%;background-color:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;\">"
                + "<tr><td style=\"background-color:#b91c1c;padding:24px 32px;\">"
                + "<span style=\"font-size:20px;font-weight:bold;color:#ffffff;letter-spacing:0.3px;\">&#128205; Explore Ceylon</span>"
                + "</td></tr>"
                + "<tr><td style=\"padding:32px;\">"
                + "<div style=\"text-align:center;margin-bottom:20px;\">"
                + "<span style=\"display:inline-block;font-size:40px;\">&#10060;</span>"
                + "<h2 style=\"margin:8px 0 0 0;color:#111827;font-size:20px;\">Identity Verification Update</h2>"
                + "</div>"
                + "<p style=\"margin:0 0 16px 0;font-size:16px;color:#111827;\">Hi " + escape(greetingName) + ",</p>"
                + "<p style=\"margin:0 0 16px 0;font-size:14px;line-height:1.6;color:#374151;\">"
                + "Your identity verification submission could not be approved at this time."
                + "</p>"
                + "<div style=\"background-color:#fef2f2;border-left:4px solid #ef4444;padding:12px 16px;margin-bottom:20px;border-radius:4px;\">"
                + "<p style=\"margin:0;font-size:13px;font-weight:bold;color:#991b1b;\">Reason for rejection:</p>"
                + "<p style=\"margin:4px 0 0 0;font-size:14px;color:#7f1d1d;\">" + escape(rejectionReason) + "</p>"
                + "</div>"
                + "<p style=\"margin:0 0 24px 0;font-size:14px;line-height:1.6;color:#374151;\">"
                + "Please sign in to Explore Ceylon and visit the Verification page to upload a new, clear photo of your ID document."
                + "</p>"
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 32px;background-color:#f9fafb;border-top:1px solid #e5e7eb;\">"
                + "<p style=\"margin:0;font-size:12px;color:#9ca3af;\">&copy; Explore Ceylon &mdash; Discover Sri Lanka.</p>"
                + "</td></tr>"
                + "</table>"
                + "</td></tr></table>"
                + "</body></html>";
    }

    static String kycRejectedPlainText(String recipientName, String reason) {
        String greetingName = (recipientName == null || recipientName.isBlank()) ? "there" : recipientName;
        String rejectionReason = (reason == null || reason.isBlank()) ? "Document was unreadable or invalid." : reason;
        return "Hi " + greetingName + ",\n\n"
                + "Your identity verification submission could not be approved at this time.\n\n"
                + "Reason: " + rejectionReason + "\n\n"
                + "Please sign in to Explore Ceylon and visit the Verification page to resubmit your ID document.\n\n"
                + "Explore Ceylon";
    }

    static String bookingConfirmedHtml(com.exploreceylon.backend.dto.email.BookingConfirmationDetails d) {
        String greetingName = (d.getCustomerName() == null || d.getCustomerName().isBlank()) ? "Traveler" : d.getCustomerName();
        boolean isVehicle = "VEHICLE".equalsIgnoreCase(d.getBookingType());
        String bookingRef = (d.getReferenceId() != null && !d.getReferenceId().isBlank())
                ? d.getReferenceId()
                : ((isVehicle ? "VBK-" : "GBK-") + (d.getBookingId() != null ? d.getBookingId() : ""));
        String bookingTypeTitle = isVehicle ? "Vehicle Booking" : "Tour Guide Booking";
        String currency = d.getCurrency() != null ? d.getCurrency() : "USD";
        double totalCost = d.getTotalCost() != null ? d.getTotalCost() : 0.0;
        double advanceAmount = d.getAdvanceAmount() != null ? d.getAdvanceAmount() : 0.0;
        double balanceAmount = d.getBalanceAmount() != null ? d.getBalanceAmount() : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>")
          .append("<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>")
          .append("<body style=\"margin:0;padding:0;background-color:#f4f6f5;font-family:Arial,Helvetica,sans-serif;\">")
          .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f6f5;padding:24px 0;\">")
          .append("<tr><td align=\"center\">")
          .append("<table role=\"presentation\" width=\"540\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:540px;width:100%;background-color:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;\">")
          .append("<tr><td style=\"background-color:").append(BRAND_GREEN).append(";padding:24px 32px;\">")
          .append("<span style=\"font-size:20px;font-weight:bold;color:#ffffff;letter-spacing:0.3px;\">&#128205; Explore Ceylon</span>")
          .append("</td></tr>")
          .append("<tr><td style=\"padding:32px 32px 16px 32px;\">")
          .append("<div style=\"text-align:center;margin-bottom:20px;\">")
          .append("<span style=\"display:inline-block;font-size:40px;\">&#9989;</span>")
          .append("<h2 style=\"margin:8px 0 4px 0;color:#111827;font-size:22px;\">Booking Confirmed!</h2>")
          .append("<p style=\"margin:0;font-size:14px;color:#059669;font-weight:bold;\">Reference: #").append(escape(bookingRef)).append("</p>")
          .append("</div>")
          .append("<p style=\"margin:0 0 16px 0;font-size:16px;color:#111827;\">Hi ").append(escape(greetingName)).append(",</p>")
          .append("<p style=\"margin:0 0 20px 0;font-size:14px;line-height:1.6;color:#374151;\">")
          .append("Thank you for your booking! Your advance payment was successful and your ")
          .append(escape(bookingTypeTitle))
          .append(" has been confirmed.")
          .append("</p>")
          .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f9fafb;border-radius:6px;border:1px solid #e5e7eb;margin-bottom:20px;padding:16px;\">")
          .append("<tr><td colspan=\"2\" style=\"padding-bottom:10px;font-size:15px;font-weight:bold;color:#111827;border-bottom:1px solid #e5e7eb;\">")
          .append("&#128197; Service Details")
          .append("</td></tr>")
          .append("<tr><td style=\"padding:8px 0 4px 0;font-size:13px;color:#6b7280;width:40%;\">Service:</td>")
          .append("<td style=\"padding:8px 0 4px 0;font-size:13px;font-weight:bold;color:#111827;\">").append(escape(bookingTypeTitle)).append("</td></tr>")
          .append("<tr><td style=\"padding:4px 0;font-size:13px;color:#6b7280;\">Dates:</td>")
          .append("<td style=\"padding:4px 0;font-size:13px;font-weight:bold;color:#111827;\">")
          .append(d.getStartDate() != null ? d.getStartDate().toString() : "N/A")
          .append(" to ")
          .append(d.getEndDate() != null ? d.getEndDate().toString() : "N/A")
          .append("</td></tr>");

        if (isVehicle) {
            if (d.getPickupTime() != null && !d.getPickupTime().isBlank()) {
                sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#6b7280;\">Pickup Time:</td>")
                  .append("<td style=\"padding:4px 0;font-size:13px;color:#111827;\">").append(escape(d.getPickupTime())).append("</td></tr>");
            }
            if (d.getDropoffTime() != null && !d.getDropoffTime().isBlank()) {
                sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#6b7280;\">Dropoff Time:</td>")
                  .append("<td style=\"padding:4px 0;font-size:13px;color:#111827;\">").append(escape(d.getDropoffTime())).append("</td></tr>");
            }
            if (d.getPickupLocation() != null && !d.getPickupLocation().isBlank()) {
                sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#6b7280;\">Pickup Location:</td>")
                  .append("<td style=\"padding:4px 0;font-size:13px;color:#111827;\">").append(escape(d.getPickupLocation())).append("</td></tr>");
            }
            if (d.getDropoffLocation() != null && !d.getDropoffLocation().isBlank()) {
                sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#6b7280;\">Dropoff Location:</td>")
                  .append("<td style=\"padding:4px 0;font-size:13px;color:#111827;\">").append(escape(d.getDropoffLocation())).append("</td></tr>");
            }
            if (d.getVehicleNumber() != null && !d.getVehicleNumber().isBlank()) {
                sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#6b7280;\">Vehicle Plate:</td>")
                  .append("<td style=\"padding:4px 0;font-size:13px;font-weight:bold;color:#111827;\">").append(escape(d.getVehicleNumber())).append("</td></tr>");
            }
        }
        if (d.getNotes() != null && !d.getNotes().isBlank()) {
            sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#6b7280;\">Special Requirements:</td>")
              .append("<td style=\"padding:4px 0;font-size:13px;color:#111827;\">").append(escape(d.getNotes())).append("</td></tr>");
        }
        if (d.getTripTitle() != null && !d.getTripTitle().isBlank()) {
            sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#6b7280;\">Related Trip:</td>")
              .append("<td style=\"padding:4px 0;font-size:13px;color:#111827;\">").append(escape(d.getTripTitle())).append("</td></tr>");
        }
        sb.append("</table>");

        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f0fdf4;border-radius:6px;border:1px solid #bbf7d0;margin-bottom:20px;padding:16px;\">")
          .append("<tr><td colspan=\"2\" style=\"padding-bottom:10px;font-size:15px;font-weight:bold;color:#166534;border-bottom:1px solid #bbf7d0;\">")
          .append("&#128100; Provider & Contact Information")
          .append("</td></tr>")
          .append("<tr><td style=\"padding:8px 0 4px 0;font-size:13px;color:#166534;width:40%;\">Provider Name:</td>")
          .append("<td style=\"padding:8px 0 4px 0;font-size:13px;font-weight:bold;color:#111827;\">")
          .append(escape(d.getProviderName() != null ? d.getProviderName() : "N/A"))
          .append("</td></tr>");

        if (d.getProviderPhone() != null && !d.getProviderPhone().isBlank()) {
            sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#166534;\">Phone:</td>")
              .append("<td style=\"padding:4px 0;font-size:13px;font-weight:bold;color:#111827;\">").append(escape(d.getProviderPhone())).append("</td></tr>");
        }
        if (d.getProviderWhatsapp() != null && !d.getProviderWhatsapp().isBlank()) {
            sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#166534;\">WhatsApp:</td>")
              .append("<td style=\"padding:4px 0;font-size:13px;font-weight:bold;color:#059669;\">").append(escape(d.getProviderWhatsapp())).append("</td></tr>");
        }
        if (d.getProviderEmail() != null && !d.getProviderEmail().isBlank()) {
            sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#166534;\">Email:</td>")
              .append("<td style=\"padding:4px 0;font-size:13px;color:#111827;\">").append(escape(d.getProviderEmail())).append("</td></tr>");
        }
        if (d.getProviderDistrict() != null && !d.getProviderDistrict().isBlank()) {
            sb.append("<tr><td style=\"padding:4px 0;font-size:13px;color:#166534;\">District / Area:</td>")
              .append("<td style=\"padding:4px 0;font-size:13px;color:#111827;\">").append(escape(d.getProviderDistrict())).append("</td></tr>");
        }
        sb.append("</table>");

        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f8fafc;border-radius:6px;border:1px solid #e2e8f0;margin-bottom:24px;padding:16px;\">")
          .append("<tr><td colspan=\"2\" style=\"padding-bottom:10px;font-size:15px;font-weight:bold;color:#0f172a;border-bottom:1px solid #e2e8f0;\">")
          .append("&#128179; Payment Summary")
          .append("</td></tr>")
          .append("<tr><td style=\"padding:8px 0 4px 0;font-size:13px;color:#64748b;\">Total Cost:</td>")
          .append("<td style=\"padding:8px 0 4px 0;font-size:13px;font-weight:bold;color:#0f172a;\">").append(String.format(java.util.Locale.US, "%.2f %s", totalCost, currency)).append("</td></tr>")
          .append("<tr><td style=\"padding:4px 0;font-size:13px;color:#64748b;\">Advance Paid (Deposit):</td>")
          .append("<td style=\"padding:4px 0;font-size:13px;font-weight:bold;color:#16a34a;\">&#10003; ").append(String.format(java.util.Locale.US, "%.2f %s", advanceAmount, currency)).append(" (Confirmed)</td></tr>")
          .append("<tr><td style=\"padding:4px 0;font-size:13px;color:#64748b;\">Remaining Balance:</td>")
          .append("<td style=\"padding:4px 0;font-size:13px;font-weight:bold;color:#d97706;\">").append(String.format(java.util.Locale.US, "%.2f %s", balanceAmount, currency)).append(" (Due on completion)</td></tr>")
          .append("</table>")
          .append("<p style=\"margin:0 0 16px 0;font-size:13px;line-height:1.5;color:#6b7280;\">")
          .append("Please reach out to your provider directly via WhatsApp or phone if you need to coordinate arrival details or ask any questions.")
          .append("</p>")
          .append("</td></tr>")
          .append("<tr><td style=\"padding:20px 32px;background-color:#f9fafb;border-top:1px solid #e5e7eb;\">")
          .append("<p style=\"margin:0;font-size:12px;color:#9ca3af;\">&copy; Explore Ceylon &mdash; Discover Sri Lanka.</p>")
          .append("</td></tr>")
          .append("</table>")
          .append("</td></tr></table>")
          .append("</body></html>");

        return sb.toString();
    }

    static String bookingConfirmedPlainText(com.exploreceylon.backend.dto.email.BookingConfirmationDetails d) {
        String greetingName = (d.getCustomerName() == null || d.getCustomerName().isBlank()) ? "Traveler" : d.getCustomerName();
        boolean isVehicle = "VEHICLE".equalsIgnoreCase(d.getBookingType());
        String bookingRef = (d.getReferenceId() != null && !d.getReferenceId().isBlank())
                ? d.getReferenceId()
                : ((isVehicle ? "VBK-" : "GBK-") + (d.getBookingId() != null ? d.getBookingId() : ""));
        String bookingTypeTitle = isVehicle ? "Vehicle Booking" : "Tour Guide Booking";
        String currency = d.getCurrency() != null ? d.getCurrency() : "USD";
        double totalCost = d.getTotalCost() != null ? d.getTotalCost() : 0.0;
        double advanceAmount = d.getAdvanceAmount() != null ? d.getAdvanceAmount() : 0.0;
        double balanceAmount = d.getBalanceAmount() != null ? d.getBalanceAmount() : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(greetingName).append(",\n\n")
          .append("Your Explore Ceylon ").append(bookingTypeTitle).append(" is CONFIRMED!\n\n")
          .append("Booking Reference: #").append(bookingRef).append("\n")
          .append("Dates: ").append(d.getStartDate()).append(" to ").append(d.getEndDate()).append("\n");

        if (isVehicle) {
            if (d.getVehicleNumber() != null) sb.append("Vehicle Plate: ").append(d.getVehicleNumber()).append("\n");
            if (d.getPickupLocation() != null) sb.append("Pickup Location: ").append(d.getPickupLocation()).append("\n");
            if (d.getDropoffLocation() != null) sb.append("Dropoff Location: ").append(d.getDropoffLocation()).append("\n");
            if (d.getPickupTime() != null) sb.append("Pickup Time: ").append(d.getPickupTime()).append("\n");
            if (d.getDropoffTime() != null) sb.append("Dropoff Time: ").append(d.getDropoffTime()).append("\n");
        }
        if (d.getNotes() != null && !d.getNotes().isBlank()) {
            sb.append("Special Requirements: ").append(d.getNotes()).append("\n");
        }

        sb.append("\nProvider Information:\n")
          .append("Name: ").append(d.getProviderName() != null ? d.getProviderName() : "N/A").append("\n");
        if (d.getProviderPhone() != null) sb.append("Phone: ").append(d.getProviderPhone()).append("\n");
        if (d.getProviderWhatsapp() != null) sb.append("WhatsApp: ").append(d.getProviderWhatsapp()).append("\n");
        if (d.getProviderEmail() != null) sb.append("Email: ").append(d.getProviderEmail()).append("\n");
        if (d.getProviderDistrict() != null) sb.append("District: ").append(d.getProviderDistrict()).append("\n");

        sb.append("\nPayment Summary:\n")
          .append("Total Cost: ").append(String.format(java.util.Locale.US, "%.2f %s", totalCost, currency)).append("\n")
          .append("Advance Paid: ").append(String.format(java.util.Locale.US, "%.2f %s", advanceAmount, currency)).append(" (Confirmed)\n")
          .append("Remaining Balance: ").append(String.format(java.util.Locale.US, "%.2f %s", balanceAmount, currency)).append(" (Due on completion)\n\n")
          .append("Explore Ceylon — Discover Sri Lanka");

        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
