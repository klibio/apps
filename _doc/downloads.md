---
title: Downloads
nav_order: 2
---
# Latest Build Artifacts

This page provides a stable link to the latest OSGi bundle artifacts from the last successful build on the `main` branch.
Artifact information is loaded from the [GitHub Releases API](https://docs.github.com/en/rest/releases/releases#get-a-release-by-tag-name).

<div id="build-info">
  <p id="loading-msg">&#9203; Loading latest build information from GitHub&hellip;</p>

  <div id="build-details" style="display:none">
    <blockquote>
      <strong>Latest Build:</strong>
      Release <a id="run-link" href="#"><span id="run-number"></span></a>
      &nbsp;|&nbsp;
      <strong>Commit:</strong> <a id="commit-link" href="#"><span id="commit-sha"></span></a>
      &nbsp;|&nbsp;
      <strong>Published:</strong> <span id="run-date"></span>
    </blockquote>

    <h2>OSGi Bundles</h2>
    <table>
      <thead>
        <tr>
          <th>Bundle</th>
          <th>Description</th>
          <th>Download</th>
        </tr>
      </thead>
      <tbody id="bundles-table"></tbody>
    </table>

    <blockquote>
      &#8505;&#65039; <strong>Note:</strong> Downloading artifacts requires a
      <a href="https://github.com/login">GitHub account</a>.
      If a download link does not start automatically, please log in to GitHub first.
    </blockquote>
  </div>

  <div id="error-msg" style="display:none">
    <p>&#9888;&#65039; Could not load artifact information automatically.</p>
    <p>
      Please visit the
      <a href="https://github.com/klibio/apps/releases/tag/latest-main">
        latest-main release page
      </a>
      and download artifacts manually.
    </p>
  </div>
</div>

<script>
(function () {
  'use strict';

  var REPO        = 'klibio/apps';
  var RELEASE_TAG = 'latest-main';

  /* Human-readable metadata for every artifact produced by the build workflow */
  var ARTIFACT_META = {
    'io.klib.app.p2.mirror': {
      bundle: 'io.klib.app.p2.mirror',
      description: 'Eclipse p2 repository mirroring application bundle'
    },
    'io.klib.app.p2.mirror.api': {
      bundle: 'io.klib.app.p2.mirror.api',
      description: 'Eclipse p2 mirror API bundle (exported packages)'
    }
  };

  function esc(str) {
    var d = document.createElement('div');
    d.appendChild(document.createTextNode(String(str)));
    return d.innerHTML;
  }

  function fetchJSON(url) {
    return fetch(url).then(function (r) {
      if (!r.ok) { throw new Error('HTTP ' + r.status + ' for ' + url); }
      return r.json();
    });
  }

  function loadArtifacts() {
    var releaseUrl = 'https://api.github.com/repos/' + REPO +
      '/releases/tags/' + RELEASE_TAG;

    fetchJSON(releaseUrl)
      .then(function (release) {
        renderPage(release, release.assets || []);
      })
      .catch(function (err) {
        console.error('Failed to load artifacts:', err);
        document.getElementById('loading-msg').style.display = 'none';
        document.getElementById('error-msg').style.display = 'block';
      });
  }

  function renderPage(release, assets) {
    var releaseLabel = release.name || release.tag_name || RELEASE_TAG;
    var releaseTime  = release.published_at || release.created_at;
    var headSha      = release.target_commitish || '';

    document.getElementById('run-link').href        = release.html_url;
    document.getElementById('run-number').textContent = releaseLabel;
    document.getElementById('commit-link').href     = 'https://github.com/' + REPO + '/commit/' + headSha;
    document.getElementById('commit-sha').textContent = headSha ? String(headSha).substring(0, 12) : 'n/a';
    document.getElementById('run-date').textContent = releaseTime ? new Date(releaseTime).toUTCString() : 'n/a';

    var bundlesTbody = document.getElementById('bundles-table');

    /* Index assets by the stem of their filename (without version / extension) */
    var byPrefix = {};
    assets.forEach(function (a) {
      var stem = a.name.replace(/[-_]\d+\.\d+\.\d+.*$/, '').replace(/\.jar$/, '');
      if (!byPrefix[stem]) { byPrefix[stem] = []; }
      byPrefix[stem].push(a);
    });

    /* Render known bundles first, then any remaining JARs */
    var rendered = {};

    Object.keys(ARTIFACT_META).sort().forEach(function (key) {
      var meta   = ARTIFACT_META[key];
      var matched = byPrefix[key] || [];
      rendered[key] = true;

      if (matched.length === 0) {
        /* Try full name match as fallback */
        assets.forEach(function (a) {
          if (a.name.startsWith(key)) { matched.push(a); }
        });
      }

      matched.sort(function (a, b) { return a.name.localeCompare(b.name); });
      var downloadCells = matched.length
        ? matched.map(function (a) {
            return '<a href="' + esc(a.browser_download_url) + '">\u2b07 ' + esc(a.name) + '</a>';
          }).join('<br>')
        : '<em>not available</em>';

      var row = document.createElement('tr');
      row.innerHTML =
        '<td><code>' + esc(meta.bundle) + '</code></td>' +
        '<td>' + esc(meta.description) + '</td>' +
        '<td>' + downloadCells + '</td>';
      bundlesTbody.appendChild(row);
    });

    /* Render any additional JARs not covered by ARTIFACT_META */
    var extras = assets.filter(function (a) {
      return a.name.endsWith('.jar') &&
             !Object.keys(rendered).some(function (k) { return a.name.startsWith(k); });
    });
    extras.sort(function (a, b) { return a.name.localeCompare(b.name); });
    extras.forEach(function (a) {
      var row = document.createElement('tr');
      row.innerHTML =
        '<td><code>' + esc(a.name.replace(/\.jar$/, '')) + '</code></td>' +
        '<td>&mdash;</td>' +
        '<td><a href="' + esc(a.browser_download_url) + '">\u2b07 ' + esc(a.name) + '</a></td>';
      bundlesTbody.appendChild(row);
    });

    document.getElementById('loading-msg').style.display   = 'none';
    document.getElementById('build-details').style.display = 'block';
  }

  loadArtifacts();
}());
</script>

<sup>last edit: {{ 'now' | date: "%Y%m%d-%H%M%S" }}</sup>
