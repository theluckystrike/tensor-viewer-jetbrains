// Stand-in for node:fs in the page bundle. The core's Node FileSource is never used in the IDE: the page
// reads the file by byte range from the plugin's private origin (see tensor/shim.js).
const off = () => { throw new Error('Direct file access is not available in the viewer.'); };
export const promises = { open: off };
export const openSync = off, readSync = off, readFileSync = off;
export default { promises, openSync, readSync, readFileSync };
