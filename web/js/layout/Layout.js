// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import "purecss/build/pure-min.css";
import "purecss/build/grids-responsive-min.css";
import "./Layout.css";
import * as React from 'react';
import Link from "./Link";
import LanguageSelection from "./LanguageSelection";
import CongregationSelection from "./CongregationSelection";
import AuthenticationPanel from "./AuthenticationPanel";
import {useSettings} from "../api";

type Props = {
  title?: string,
  children?: React.Node,
}

class Layout extends React.Component<Props> {
  componentDidMount() {
    const {title} = this.props;
    const site = "Territory Bro";
    document.title = (title ? `${title} - ${site}` : site);
  }

  render() {
    const settings = useSettings();
    const loggedIn = settings.user.authenticated;
    const {children} = this.props;
    return (
      <div id="layout">

        <nav className="no-print">
          <AuthenticationPanel/>
          <CongregationSelection/>
          <ul>
            <li><Link href="/">Overview</Link></li>
            {loggedIn &&
            <>
              <li><Link href="/territory-cards">Territory Cards</Link></li>
              <li><Link href="/neighborhood-maps">Neighborhood Maps</Link></li>
              <li><Link href="/rural-territory-cards">Rural Territory Cards</Link></li>
              <li><Link href="/region-maps">Region Maps</Link></li>
            </>
            }
          </ul>
          {loggedIn &&
          <LanguageSelection/>
          }
        </nav>

        <div className="container">
          {children}
        </div>

      </div>
    );
  }
}

export default Layout;
