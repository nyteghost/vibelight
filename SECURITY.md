# Security

## Reporting a problem

If you find a security problem in Vibelight, please **do not open a public issue**. Use GitHub's private
reporting instead: open this repository's **Security** tab and choose **Report a vulnerability**. Include
what you did, what you expected and what happened, and the Vibelight version (Settings, Support).

You will get a reply as soon as the maintainer sees it. Fixes are released as a new version and mentioned
in the release notes.

## Checking a download

Release files carry a build provenance attestation made by the release workflow. To confirm a file really
came from this repository's workflow, install the GitHub CLI and run:

    gh attestation verify <downloaded file> -R nyteghost/vibelight

Vibelight's in-app updater only installs a download whose checksum matched.

## Credit

Vibelight is a fork of [Moonlight](https://github.com/moonlight-stream/moonlight-qt). Problems that also
affect upstream Moonlight should be reported to that project as well.
