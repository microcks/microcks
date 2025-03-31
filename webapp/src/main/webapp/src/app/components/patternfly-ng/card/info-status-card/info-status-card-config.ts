/**
 * A config containing properties for the info status card
 */
export class InfoStatusCardConfig {
  /**
   * Flag to allow parsing of HTML content within the info options
   */
  htmlContent!: boolean;

  /**
   * Show/hide the top border, true shows top border, false (default) hides top border
   */
  showTopBorder?: boolean;

  /**
   * The main title of the info status card
   */
  title?: string;

  /**
   * The href to navigate to if one clicks on the title or count
   */
  href?: string;

  /**
   * An icon to display to the left of the count
   */
  iconStyleClass?: string;

  /**
   * An image to display to the left of Infrastructure
   */
  iconImageSrc?: string;

  /**
   * An array of strings to display, each element in the array is on a new line, accepts HTML content
   */
  info?: string[];
}
