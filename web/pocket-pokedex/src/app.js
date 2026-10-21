(function () {
  "use strict";

  var data = window.OVERDEX_DATA;
  var $ = function (id) { return document.getElementById(id); };
  if (!data || data.schemaVersion !== 1 || !Array.isArray(data.pokemon) || !data.pokemon.length) {
    $("list-view").hidden = true;
    $("load-error").hidden = false;
    document.querySelectorAll(".softkeys button").forEach(function (button) { button.disabled = true; });
    return;
  }

  var pokemon = data.pokemon;
  var byId = new Map(pokemon.map(function (entry) { return [entry.id, entry]; }));
  var storageKey = "overdex-pocket-v1";
  var favorites = new Set();
  var restoredId = 1;
  var noticeTimer;
  try {
    var saved = JSON.parse(localStorage.getItem(storageKey) || "{}");
    if (saved && Array.isArray(saved.favorites)) {
      saved.favorites.forEach(function (id) { if (byId.has(id)) favorites.add(id); });
    }
    if (saved && byId.has(saved.lastId)) restoredId = saved.lastId;
  } catch (_) { /* A blocked store or an old/broken value must not prevent browsing. */ }

  var state = {
    view: "list", origin: "list", id: restoredId, listIndex: restoredId - 1,
    savedOnly: false, searchIndex: 0, query: "", results: pokemon
  };

  function normalize(value) {
    return value.toLowerCase().replace(/♀/g, "female").replace(/♂/g, "male")
      .normalize("NFKD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]/g, "");
  }
  var searchNames = pokemon.map(function (entry) { return normalize(entry.name); });

  function persist() {
    try {
      localStorage.setItem(storageKey, JSON.stringify({ favorites: Array.from(favorites), lastId: state.id }));
      return true;
    } catch (_) { return false; }
  }

  function notify(message) {
    clearTimeout(noticeTimer);
    $("notice").textContent = message;
    $("notice").hidden = false;
    noticeTimer = setTimeout(function () { $("notice").hidden = true; }, 1900);
  }

  function number(id) { return "#" + String(id).padStart(4, "0"); }
  function spriteUrl(id) {
    return window.OVERDEX_SPRITES ? window.OVERDEX_SPRITES[String(id)] : "sprites/" + id + ".png";
  }
  function listEntries() {
    return state.savedOnly ? pokemon.filter(function (entry) { return favorites.has(entry.id); }) : pokemon;
  }
  function pageSize(container) {
    var rowHeight = parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--row-height")) || 40;
    return Math.max(1, Math.floor(container.clientHeight / rowHeight));
  }
  function keys(left, center, right, canOpen) {
    $("soft-left").textContent = left;
    $("soft-center").textContent = center;
    $("soft-right").textContent = right;
    $("soft-center").disabled = canOpen === false;
  }

  function renderRows(container, entries, index, emptyMessage, activate) {
    container.textContent = "";
    container.removeAttribute("aria-activedescendant");
    if (!entries.length) {
      var empty = document.createElement("p");
      empty.className = "empty-state";
      empty.textContent = emptyMessage;
      container.appendChild(empty);
      return;
    }
    var count = pageSize(container);
    var start = Math.floor(index / count) * count;
    entries.slice(start, start + count).forEach(function (entry, offset) {
      var row = document.createElement("button");
      row.type = "button";
      row.className = "dex-row";
      row.id = container.id + "-" + entry.id;
      row.tabIndex = -1;
      row.setAttribute("role", "option");
      row.setAttribute("aria-selected", String(start + offset === index));
      row.dataset.id = entry.id;
      var image = document.createElement("img");
      image.src = spriteUrl(entry.id);
      image.alt = "";
      image.width = 36;
      image.height = 36;
      var copy = document.createElement("span");
      copy.className = "row-copy";
      var title = document.createElement("span");
      title.className = "row-name";
      title.textContent = entry.name;
      var subtitle = document.createElement("span");
      subtitle.className = "row-number";
      subtitle.textContent = number(entry.id) + " · " + entry.region.toUpperCase();
      copy.appendChild(title);
      copy.appendChild(subtitle);
      var star = document.createElement("span");
      star.className = "row-star";
      star.textContent = favorites.has(entry.id) ? "★" : "";
      row.appendChild(image);
      row.appendChild(copy);
      row.appendChild(star);
      row.addEventListener("click", function () { activate(start + offset); });
      container.appendChild(row);
      if (start + offset === index) container.setAttribute("aria-activedescendant", row.id);
    });
  }

  function renderList() {
    var entries = listEntries();
    state.listIndex = Math.max(0, Math.min(state.listIndex, entries.length - 1));
    $("list-title").textContent = state.savedOnly ? "SAVED FAVORITES" : "NATIONAL INDEX";
    $("list-count").textContent = String(entries.length).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    renderRows($("list-items"), entries, state.listIndex,
      "No favorites yet. Open a Pokémon and press SAVE to keep it here.", function (index) {
        state.listIndex = index;
        openSelected();
      });
    $("list-position").textContent = entries.length ? (state.listIndex + 1) + " / " + entries.length : "0 SAVED";
    keys("FIND", "OPEN", state.savedOnly ? "ALL" : "SAVED", entries.length > 0);
  }

  function renderDetail() {
    var entry = byId.get(state.id);
    $("detail-number").textContent = number(entry.id);
    $("detail-region").textContent = entry.region.toUpperCase();
    $("detail-star").textContent = favorites.has(entry.id) ? "★" : "";
    $("detail-name").textContent = entry.name;
    $("detail-types").textContent = "";
    entry.types.forEach(function (type) {
      var badge = document.createElement("span");
      badge.className = "type type-" + type;
      badge.textContent = type;
      $("detail-types").appendChild(badge);
    });
    var image = $("detail-sprite");
    if (image.dataset.id !== String(entry.id)) {
      image.hidden = false;
      $("sprite-missing").hidden = true;
      image.dataset.id = entry.id;
      image.alt = entry.name;
      image.src = spriteUrl(entry.id);
    }
    $("detail-genus").textContent = entry.genus;
    $("stat-values").textContent = "";
    ["ATK", "DEF", "STA"].forEach(function (label, index) {
      var stat = document.createElement("div");
      stat.className = "stat";
      var caption = document.createElement("span");
      caption.textContent = label;
      var value = document.createElement("strong");
      value.textContent = entry.stats[index];
      stat.appendChild(caption);
      stat.appendChild(value);
      $("stat-values").appendChild(stat);
    });
    keys(favorites.has(entry.id) ? "★ SAVED" : "☆ SAVE", "INFO", "BACK");
  }

  function renderInfo() {
    var entry = byId.get(state.id);
    $("info-number").textContent = number(entry.id);
    $("info-name").textContent = entry.name;
    $("info-genus").textContent = entry.genus;
    $("info-description").textContent = entry.description;
    $("info-form").textContent = "Stats and types: " + entry.referenceForm + ".";
    $("info-date").textContent = "Pokémon GO reference: " + data.referenceDate + ".";
    $("info-release").textContent = entry.released ? "Base stats, before individual values or level scaling."
      : "Not marked released in this reference. GO stats may be provisional.";
    keys(favorites.has(entry.id) ? "★ SAVED" : "☆ SAVE", "SPRITE", "BACK");
  }

  function updateSearch() {
    state.query = $("search-input").value;
    var trimmed = state.query.trim();
    var query = normalize(trimmed);
    if (/^#?\s*\d+$/.test(trimmed)) {
      var id = Number(trimmed.replace(/[^0-9]/g, ""));
      state.results = byId.has(id) ? [byId.get(id)] : [];
    } else {
      state.results = query ? pokemon.filter(function (_, index) { return searchNames[index].indexOf(query) >= 0; }) : pokemon;
    }
    state.searchIndex = 0;
    renderSearch();
  }

  function renderSearch() {
    $("search-count").textContent = state.results.length + (state.results.length === 1 ? " MATCH" : " MATCHES");
    renderRows($("search-items"), state.results, state.searchIndex,
      "No match. Try a species name or a number from 1 to " + pokemon.length + ".", function (index) {
        state.searchIndex = index;
        openSelected();
      });
    keys("CLEAR", "OPEN", "BACK", state.results.length > 0);
  }

  function render() {
    ["list", "detail", "info", "search"].forEach(function (view) { $(view + "-view").hidden = state.view !== view; });
    $("instrument").dataset.view = state.view;
    if (state.view === "list") renderList();
    if (state.view === "detail") renderDetail();
    if (state.view === "info") renderInfo();
    if (state.view === "search") renderSearch();
  }

  function openSelected() {
    var entry = state.view === "search" ? state.results[state.searchIndex] : listEntries()[state.listIndex];
    if (!entry) return;
    state.origin = state.view;
    state.id = entry.id;
    state.view = "detail";
    if (document.activeElement) document.activeElement.blur();
    persist();
    render();
  }

  function openSearch(initial) {
    state.view = "search";
    $("search-input").value = initial || "";
    render();
    updateSearch();
    $("search-input").focus();
  }

  function toggleSavedList() {
    var previous = listEntries()[state.listIndex];
    state.savedOnly = !state.savedOnly;
    var entries = listEntries();
    state.listIndex = Math.max(0, entries.findIndex(function (entry) { return previous && entry.id === previous.id; }));
    render();
    $("list-items").focus();
  }

  function toggleFavorite() {
    if (favorites.has(state.id)) favorites.delete(state.id); else favorites.add(state.id);
    var stored = persist();
    render();
    notify(stored ? (favorites.has(state.id) ? "Added to favorites" : "Removed from favorites")
      : "Saved for this visit. Browser storage is unavailable.");
  }

  function center() {
    if (state.view === "list" || state.view === "search") openSelected();
    else {
      state.view = state.view === "detail" ? "info" : "detail";
      render();
      if (state.view === "info") { $("info-scroll").scrollTop = 0; $("info-scroll").focus(); }
    }
  }

  function back() {
    if (state.view === "info") state.view = "detail";
    else if (state.view === "detail") state.view = state.origin;
    else if (state.view === "search") state.view = "list";
    else if (state.savedOnly) { toggleSavedList(); return true; }
    else return false; // Let the browser/phone handle Back from the root index.
    render();
    if (state.view === "list") $("list-items").focus();
    if (state.view === "search") $("search-items").focus();
    return true;
  }

  function left() {
    if (state.view === "list") openSearch();
    else if (state.view === "search") { $("search-input").value = ""; updateSearch(); $("search-input").focus(); }
    else toggleFavorite();
  }
  function right() { if (state.view === "list") toggleSavedList(); else back(); }

  function move(key) {
    var horizontal = key === "ArrowLeft" || key === "ArrowRight";
    var direction = key === "ArrowUp" || key === "ArrowLeft" ? -1 : 1;
    if (state.view === "list") {
      var entries = listEntries();
      var step = horizontal ? pageSize($("list-items")) : 1;
      state.listIndex = Math.max(0, Math.min(entries.length - 1, state.listIndex + direction * step));
      renderList();
      $("list-items").focus();
    } else if (state.view === "search") {
      if (document.activeElement === $("search-input") && horizontal) return false;
      if (document.activeElement === $("search-input") && direction > 0) {
        $("search-items").focus();
      } else if (state.searchIndex === 0 && direction < 0 && !horizontal) {
        $("search-input").focus();
        return true;
      } else {
        var searchStep = horizontal ? pageSize($("search-items")) : 1;
        state.searchIndex = Math.max(0, Math.min(state.results.length - 1, state.searchIndex + direction * searchStep));
      }
      renderSearch();
    } else if (horizontal) {
      state.id = ((state.id - 1 + direction + pokemon.length) % pokemon.length) + 1;
      persist();
      render();
      $("info-scroll").scrollTop = 0;
    } else if (state.view === "info") {
      $("info-scroll").scrollTop += direction * 45;
    } else if (direction > 0) {
      center();
    }
    return true;
  }

  $("soft-left").addEventListener("click", left);
  $("soft-center").addEventListener("click", center);
  $("soft-right").addEventListener("click", right);
  $("search-input").addEventListener("input", updateSearch);
  $("detail-sprite").addEventListener("error", function () {
    $("detail-sprite").hidden = true;
    $("sprite-missing").hidden = false;
  });
  document.addEventListener("keydown", function (event) {
    if (event.ctrlKey || event.altKey || event.metaKey || event.isComposing) return;
    var key = event.key;
    var editing = state.view === "search" && document.activeElement === $("search-input");
    var handled = true;
    if (event.repeat && ["SoftLeft", "SoftRight", "F1", "F2", "Enter", "*", "#"].indexOf(key) >= 0) {
      event.preventDefault();
      return;
    }
    if (key === "SoftLeft" || key === "F1") left();
    else if (key === "SoftRight" || key === "F2") right();
    else if (key === "Enter") {
      if (event.target.tagName === "BUTTON" && event.target.closest(".softkeys")) event.target.click();
      else center();
    } else if (key.indexOf("Arrow") === 0) handled = move(key);
    else if (key === "Escape" || (key === "Backspace" && !editing)) handled = back();
    else if (!editing && key === "#") openSearch();
    else if (!editing && key === "*") {
      if (state.view === "detail" || state.view === "info") toggleFavorite();
      else if (state.view === "list") toggleSavedList();
    } else if (!editing && /^[0-9]$/.test(key)) openSearch(key);
    else handled = false;
    if (handled) event.preventDefault();
  });
  window.addEventListener("resize", render);
  render();
  $("list-items").focus();
})();
