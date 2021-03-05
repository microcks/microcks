import { environment as defaultEnvironment } from './environment';

/**
 * Loads the environment available in the assets folder. This enables dynamic configuration for Docker images.
 */
export const environmentLoader = new Promise<any>((resolve, reject) => {
    const xmlhttp = new XMLHttpRequest();
    const url = './environment/environment.json';
    console.log("Loading environment from url", url)
    xmlhttp.open('GET', url, true);
    xmlhttp.onload = () => {
        if (xmlhttp.status === 200) {
            resolve(JSON.parse(xmlhttp.responseText));
        } else {
            console.log('Default env is used!');
            resolve(defaultEnvironment);
        }
    };
    xmlhttp.send();
});
