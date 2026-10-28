package com.jagrosh.jmusicbot.verification;

import com.jagrosh.jmusicbot.utils.OtherUtil;

/** Offline manifest and four-part version comparison checks. */
public class ReleaseVersionCheck
{
    private static int checks;

    public static void main(String[] args)
    {
        if (args.length != 1) throw new IllegalArgumentException("Pass the expected release version");
        require(OtherUtil.getCurrentVersion().equals(args[0]), "runtime manifest version matches release");
        require(OtherUtil.isNewerVersion("0.6.9", "0.6.9.1"), "four-part hotfix is newer than 0.6.9");
        require(!OtherUtil.isNewerVersion("0.6.9.1", "0.6.9"), "older release is not an upgrade");
        require(!OtherUtil.isNewerVersion("0.6.9.1", "0.6.9.1"), "same release is not an upgrade");
        require(OtherUtil.isNewerVersion("0.6.9.1", "0.6.9.2"), "later hotfix is an upgrade");
        require(OtherUtil.isNewerVersion("0.6.9.1", "0.6.10"), "next patch is an upgrade");
        require(!OtherUtil.isNewerVersion("0.6.9.1", "0.6.9.1.0"), "trailing zero does not create a false upgrade");
        System.out.println("RELEASE VERSION OFFLINE PASS: " + checks + " assertions");
    }

    private static void require(boolean condition, String label)
    {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
