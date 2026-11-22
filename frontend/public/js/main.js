// C-4 Bootstrap: wires the real API client (window.fetch, setTimeout) to the app and starts the first load.
// Only wiring lives here. createApp starts `load('initial')` itself (autoStart defaults to true).
//
// #boot-note tells the reader to open the page through the frontend server. It is hidden only after createApp
// returned, so if a file in the chain failed to load or createApp threw, the note stays visible (R-8).

import { createApiClient } from './api.js';
import { createApp } from './app.js';

createApp({ root: document.getElementById('app'), api: createApiClient() });

const bootNote = document.getElementById('boot-note');
if (bootNote) {
  bootNote.hidden = true;
}
